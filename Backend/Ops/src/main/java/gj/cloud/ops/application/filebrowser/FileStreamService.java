package gj.cloud.ops.application.filebrowser;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;
import gj.cloud.ops.application.filebrowser.dto.FileStreamTicketPayload;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import gj.cloud.ops.global.ssh.VmSshSessionFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// HTTP Range 요청을 지원하는 미디어 스트리밍 — JSch의 get(path, monitor, skip)이 SFTP READ 요청 자체를
// 지정 오프셋부터 보내므로, 앞부분을 다 읽어 버리는 방식이 아니라 프로토콜 레벨에서 바로 seek됨.
// <video>/<audio> 태그가 재생 위치를 옮길 때마다 이 엔드포인트로 부분 요청을 새로 보낸다.
@Slf4j
@Component
@RequiredArgsConstructor
public class FileStreamService {

    private static final int SFTP_CONNECT_TIMEOUT_MS = 10_000;
    private static final int COPY_BUFFER_SIZE = 8192;
    private static final Pattern RANGE_PATTERN = Pattern.compile("^bytes=(\\d*)-(\\d*)$");

    private static final Map<String, String> CONTENT_TYPES = Map.ofEntries(
            Map.entry("jpg", "image/jpeg"), Map.entry("jpeg", "image/jpeg"),
            Map.entry("png", "image/png"), Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"), Map.entry("bmp", "image/bmp"),
            Map.entry("svg", "image/svg+xml"), Map.entry("ico", "image/x-icon"),
            Map.entry("mp3", "audio/mpeg"), Map.entry("wav", "audio/wav"),
            Map.entry("ogg", "audio/ogg"), Map.entry("m4a", "audio/mp4"),
            Map.entry("flac", "audio/flac"), Map.entry("aac", "audio/aac"),
            Map.entry("mp4", "video/mp4"), Map.entry("webm", "video/webm"),
            Map.entry("mov", "video/quicktime"), Map.entry("mkv", "video/x-matroska"),
            Map.entry("avi", "video/x-msvideo")
    );

    private final VmSshSessionFactory sshSessionFactory;
    private final SftpPathResolver pathResolver;

    public void stream(FileStreamTicketPayload payload, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Session session = sshSessionFactory.createSession(payload.vmId(), payload.internalIp());
        ChannelSftp sftp = null;
        try {
            sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect(SFTP_CONNECT_TIMEOUT_MS);

            // OPS-SEC-005: 발급 시점에 확정해둔 경로/크기를 그대로 신뢰하지 않고, 매 요청마다 realpath로 재해석하고
            // lstat으로 현재 상태를 다시 확인한다. TTL(10분) 동안 대상이 심볼릭 링크로 교체되거나 다른 파일로
            // 바뀌었을 가능성을 차단하기 위함 — 대상이 바뀐 것으로 판단되면 티켓을 거부하고 재발급을 요구한다.
            String realPath;
            try {
                realPath = pathResolver.resolveExisting(sftp, payload.path());
            } catch (OpsException e) {
                throw new OpsException(OpsErrorCode.INVALID_TICKET);
            }
            if (!realPath.equals(payload.path())) {
                // 발급 시점의 실경로와 지금 다시 해석한 실경로가 다름 — 중간에 심볼릭 링크 등으로 대상이 바뀐 것
                throw new OpsException(OpsErrorCode.INVALID_TICKET);
            }
            SftpATTRS attrs;
            try {
                attrs = sftp.lstat(realPath);
            } catch (SftpException e) {
                throw new OpsException(OpsErrorCode.FILE_NOT_FOUND);
            }
            if (attrs.isDir()) {
                throw new OpsException(OpsErrorCode.INVALID_PATH);
            }
            if (attrs.getSize() != payload.size()) {
                // 발급 시점 이후 파일 내용이 바뀜(재업로드 등) — 안전하게 재발급을 요구
                throw new OpsException(OpsErrorCode.INVALID_TICKET);
            }
            long fileSize = attrs.getSize();

            long start = 0;
            long end = fileSize - 1;
            boolean partial = false;

            String rangeHeader = request.getHeader(HttpHeaders.RANGE);
            if (rangeHeader != null) {
                RangeResult range = parseRange(rangeHeader, fileSize);
                if (range == null) {
                    response.setStatus(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE.value());
                    response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes */" + fileSize);
                    return;
                }
                partial = true;
                start = range.start();
                end = range.end();
            }
            long length = end - start + 1;

            try (InputStream in = sftp.get(realPath, null, start)) {
                response.setContentType(contentTypeFor(realPath));
                response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
                response.setContentLengthLong(length);
                if (partial) {
                    response.setStatus(HttpStatus.PARTIAL_CONTENT.value());
                    response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + fileSize);
                } else {
                    response.setStatus(HttpStatus.OK.value());
                }
                copyExactly(in, response.getOutputStream(), length);
            }
        } catch (JSchException | SftpException e) {
            log.error("파일 스트리밍 실패: vmId={}, error={}", payload.vmId(), e.getMessage());
            throw new OpsException(OpsErrorCode.SFTP_OPERATION_FAILED);
        } finally {
            if (sftp != null && sftp.isConnected()) {
                sftp.disconnect();
            }
            if (session.isConnected()) {
                session.disconnect();
            }
        }
    }

    // OPS-SEC-006: 단일 range(`bytes=start-end`, `bytes=start-`, `bytes=-suffixLength`)만 지원.
    // 다중 range·빈 range·오버플로/음수·start>=fileSize·end<start·알 수 없는 단위는 전부 null(=416)로 명시 거부 —
    // 예전에는 다중 range가 조용히 무시되어 파일 전체가 200으로 나가고, `bytes=-N`(끝에서 N바이트)이
    // `start=0,end=N`(처음 N+1바이트)으로 잘못 해석되는 버그가 있었음.
    private RangeResult parseRange(String rangeHeader, long fileSize) {
        if (rangeHeader.contains(",")) {
            return null;
        }
        Matcher matcher = RANGE_PATTERN.matcher(rangeHeader.trim());
        if (!matcher.matches()) {
            return null;
        }
        String startGroup = matcher.group(1);
        String endGroup = matcher.group(2);
        if (startGroup.isEmpty() && endGroup.isEmpty()) {
            return null;
        }
        try {
            long start;
            long end;
            if (startGroup.isEmpty()) {
                // suffix range: bytes=-N → 파일 끝에서 N바이트
                long suffixLength = Long.parseLong(endGroup);
                if (suffixLength <= 0) {
                    return null;
                }
                start = Math.max(0, fileSize - suffixLength);
                end = fileSize - 1;
            } else {
                start = Long.parseLong(startGroup);
                end = endGroup.isEmpty() ? fileSize - 1 : Math.min(Long.parseLong(endGroup), fileSize - 1);
            }
            if (start < 0 || start >= fileSize || start > end) {
                return null;
            }
            return new RangeResult(start, end);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record RangeResult(long start, long end) {
    }

    private void copyExactly(InputStream in, OutputStream out, long length) throws IOException {
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        long remaining = length;
        while (remaining > 0) {
            int read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            if (read == -1) {
                break;
            }
            out.write(buffer, 0, read);
            remaining -= read;
        }
    }

    private String contentTypeFor(String path) {
        int dot = path.lastIndexOf('.');
        if (dot == -1) {
            return "application/octet-stream";
        }
        String ext = path.substring(dot + 1).toLowerCase();
        return CONTENT_TYPES.getOrDefault(ext, "application/octet-stream");
    }
}
