package gj.cloud.ops.application.filebrowser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;
import gj.cloud.ops.application.filebrowser.dto.FileStreamTicketPayload;
import gj.cloud.ops.application.vmclient.VmServiceClient;
import gj.cloud.ops.application.vmclient.dto.VmContextResponse;
import gj.cloud.ops.global.crypto.AesGcmCipher;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import gj.cloud.ops.global.ssh.VmSshSessionFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

// 미디어 미리보기 스트리밍 인증 — 웹 콘솔의 TerminalTicketService와 달리 GETDEL(1회성)을 쓰지 않음.
// <video>/<audio> 태그는 seek/버퍼링 때마다 Range 요청을 여러 번 보내므로 티켓이 한 번 쓰고 사라지면 재생이 끊긴다.
// 대신 짧은 TTL 동안 재사용 가능한 값으로 두고, 발급 시점(FILE_READ 권한 확인 + 실경로 해석 완료)의 결과를
// internalIp/실경로/파일크기와 암호화한 사용자 토큰을 페이로드에 담고, 매 스트리밍 요청에서 권한을 다시 조회한다.
@Slf4j
@Service
@RequiredArgsConstructor
public class FileStreamTicketService {

    private static final String KEY_PREFIX = "file-stream-ticket:";
    private static final String PERMISSION_FILE_READ = "FILE_READ";
    private static final String PERMISSION_SECRET_READ = "SECRET_READ";
    // SEC-012: 발급 시점 권한만 믿고 TTL 내내 재검증 없이 재생을 허용하면, 그 사이 권한이 회수돼도
    // (조직에서 제외 등) 계속 스트리밍이 가능했다. TTL도 10분 → 3분으로 축소해 노출 창을 줄인다.
    private static final Duration TICKET_TTL = Duration.ofMinutes(3);
    private static final int SFTP_CONNECT_TIMEOUT_MS = 10_000;

    private final VmServiceClient vmServiceClient;
    private final VmSshSessionFactory sshSessionFactory;
    private final SftpPathResolver pathResolver;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AesGcmCipher cipher;

    public String issue(String bearerToken, String vmId, String path) {
        VmContextResponse context = vmServiceClient.getContext(bearerToken, vmId);
        if (!context.hasPermission(PERMISSION_FILE_READ)) {
            throw new OpsException(OpsErrorCode.FORBIDDEN);
        }
        if (context.internalIp() == null || !"RUNNING".equals(context.status())) {
            throw new OpsException(OpsErrorCode.VM_NOT_RUNNING);
        }

        Session session = sshSessionFactory.createSession(vmId, context.internalIp());
        ChannelSftp sftp = null;
        try {
            sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect(SFTP_CONNECT_TIMEOUT_MS);
            String real = pathResolver.resolveExisting(sftp, path);
            if (SensitiveFilePolicy.isBackupPath(real)) {
                throw new OpsException(OpsErrorCode.FORBIDDEN);
            }
            // AUTHZ-001: .env/배포 시크릿/자격증명 디렉토리는 FILE_READ만으로 미리보기 스트리밍도 못 하게 함
            if (SensitiveFilePolicy.isSensitive(real) && !context.hasPermission(PERMISSION_SECRET_READ)) {
                throw new OpsException(OpsErrorCode.FORBIDDEN);
            }
            SftpATTRS attrs;
            try {
                attrs = sftp.lstat(real);
            } catch (SftpException e) {
                throw new OpsException(OpsErrorCode.FILE_NOT_FOUND);
            }
            if (attrs.isDir()) {
                throw new OpsException(OpsErrorCode.INVALID_PATH);
            }
            if (!FilePreviewPolicy.isPreviewable(real)) {
                throw new OpsException(OpsErrorCode.INVALID_PATH);
            }

            String ticket = UUID.randomUUID().toString();
            String tokenCiphertext = cipher.encrypt(bearerToken.getBytes(StandardCharsets.UTF_8));
            FileStreamTicketPayload payload = new FileStreamTicketPayload(
                    vmId, real, context.internalIp(), attrs.getSize(), tokenCiphertext);
            String json = objectMapper.writeValueAsString(payload);
            redisTemplate.opsForValue().set(KEY_PREFIX + ticket, json, TICKET_TTL);
            return ticket;
        } catch (JSchException e) {
            log.error("스트리밍 티켓 발급 실패: vmId={}, error={}", vmId, e.getMessage());
            throw new OpsException(OpsErrorCode.SFTP_OPERATION_FAILED);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("티켓 직렬화 실패", e);
        } finally {
            if (sftp != null && sftp.isConnected()) {
                sftp.disconnect();
            }
            if (session.isConnected()) {
                session.disconnect();
            }
        }
    }

    // 재생 중 여러 Range 요청이 들어오므로 조회 후 삭제하지 않고 TTL 동안 재사용 가능하게 둠.
    // SEC-012: 다만 발급 시점의 권한 확인 결과를 끝까지 신뢰하지 않고, 매 요청마다 VM 서비스에
    // FILE_READ 권한을 다시 조회한다 — 재생 도중 조직에서 제외되는 등으로 권한이 회수되면 즉시 차단.
    public Optional<FileStreamTicketPayload> validate(String ticket, String vmId) {
        String json = redisTemplate.opsForValue().get(KEY_PREFIX + ticket);
        if (json == null) {
            return Optional.empty();
        }
        try {
            FileStreamTicketPayload payload = objectMapper.readValue(json, FileStreamTicketPayload.class);
            if (!payload.vmId().equals(vmId)) {
                return Optional.empty();
            }
            byte[] tokenBytes = cipher.decrypt(payload.bearerTokenCiphertext());
            try {
                VmContextResponse context = vmServiceClient.getContext(
                        new String(tokenBytes, StandardCharsets.UTF_8), vmId);
                if (!context.hasPermission(PERMISSION_FILE_READ)
                        || !"RUNNING".equals(context.status())
                        || !payload.internalIp().equals(context.internalIp())) {
                    return Optional.empty();
                }
                log.info("AUDIT action=FILE_STREAM_TICKET_USE targetType=VM targetId={} result=ALLOWED", vmId);
            } finally {
                Arrays.fill(tokenBytes, (byte) 0);
            }
            return Optional.of(payload);
        } catch (JsonProcessingException e) {
            return Optional.empty();
        } catch (Exception e) {
            log.warn("스트리밍 티켓 권한 재검증 실패: vmId={}, error={}", vmId, e.getMessage());
            return Optional.empty();
        }
    }
}
