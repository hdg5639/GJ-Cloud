package gj.cloud.ops.application.filebrowser;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
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
    private static final Pattern RANGE_PATTERN = Pattern.compile("bytes=(\\d*)-(\\d*)");

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

    public void stream(FileStreamTicketPayload payload, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        long fileSize = payload.size();
        long start = 0;
        long end = fileSize - 1;
        boolean partial = false;

        String rangeHeader = request.getHeader(HttpHeaders.RANGE);
        if (rangeHeader != null) {
            Matcher matcher = RANGE_PATTERN.matcher(rangeHeader);
            if (matcher.matches()) {
                partial = true;
                if (!matcher.group(1).isEmpty()) {
                    start = Long.parseLong(matcher.group(1));
                }
                if (!matcher.group(2).isEmpty()) {
                    end = Long.parseLong(matcher.group(2));
                }
                end = Math.min(end, fileSize - 1);
                if (start < 0 || start > end) {
                    response.setStatus(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE.value());
                    response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes */" + fileSize);
                    return;
                }
            }
        }
        long length = end - start + 1;

        Session session = sshSessionFactory.createSession(payload.vmId(), payload.internalIp());
        ChannelSftp sftp = null;
        try {
            sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect(SFTP_CONNECT_TIMEOUT_MS);
            try (InputStream in = sftp.get(payload.path(), null, start)) {
                response.setContentType(contentTypeFor(payload.path()));
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
