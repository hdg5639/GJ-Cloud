package gj.cloud.ops.application.sshreadiness.service;

import com.jcraft.jsch.Session;
import gj.cloud.ops.application.sshreadiness.dto.SshReadinessRequest;
import gj.cloud.ops.application.sshreadiness.dto.SshReadinessResponse;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.ssh.CommandResult;
import gj.cloud.ops.global.ssh.SshCommandExecutor;
import gj.cloud.ops.global.ssh.VmSshSessionFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class VmSshReadinessService {

    private static final long COMMAND_TIMEOUT_MS = 10_000;
    private static final String KEY_MARKER = "__GAMJABOX_AUTHORIZED_KEYS__";
    private static final Pattern CLOUD_INIT_DONE =
            Pattern.compile("(?m)^status:\\s*(?:done|degraded)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CLOUD_INIT_FAILED =
            Pattern.compile("(?m)^status:\\s*(?:error|disabled)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final String INSPECT_COMMAND =
            "cloud-init status --long 2>&1; "
                    + "printf '\\n" + KEY_MARKER + "\\n'; "
                    + "if [ -r \"$HOME/.ssh/authorized_keys\" ]; then "
                    + "ssh-keygen -E sha256 -lf \"$HOME/.ssh/authorized_keys\" 2>/dev/null; "
                    + "fi";

    private final VmSshSessionFactory sshSessionFactory;
    private final SshCommandExecutor sshCommandExecutor;

    public SshReadinessResponse ensureReady(String vmId, SshReadinessRequest request) {
        Optional<String> canonicalKey = canonicalKey(request.expectedUserPublicKey(),
                request.expectedUserKeyFingerprint());
        if (canonicalKey.isEmpty()) {
            return SshReadinessResponse.failed("KEY_VALIDATION", "선택한 사용자 SSH 키가 유효하지 않습니다.");
        }

        Session session;
        try {
            Optional<Session> sessionOptional = sshSessionFactory.tryCreateSession(vmId, request.internalIp());
            if (sessionOptional.isEmpty()) {
                return SshReadinessResponse.pending("SSH_CONNECTING", "SSH와 관리 키 인증을 기다리고 있습니다.");
            }
            session = sessionOptional.get();
        } catch (OpsException e) {
            return SshReadinessResponse.failed("MANAGEMENT_KEY", "VM 관리 키를 사용할 수 없습니다.");
        }

        try {
            Inspection inspection = inspect(session, request.expectedUserKeyFingerprint());
            if (!inspection.cloudInitComplete()) {
                if (inspection.cloudInitFailed()) {
                    return SshReadinessResponse.failed("CLOUD_INIT", "cloud-init 초기화가 실패했습니다.");
                }
                return SshReadinessResponse.pending("CLOUD_INIT", "cloud-init 초기화를 기다리고 있습니다.");
            }
            if (inspection.userKeyPresent()) {
                return SshReadinessResponse.success();
            }

            log.warn("사용자 SSH 키 누락 감지 — 관리 키로 복구: vmId={}, fingerprint={}",
                    vmId, request.expectedUserKeyFingerprint());
            repairUserKey(session, canonicalKey.get());

            Inspection repaired = inspect(session, request.expectedUserKeyFingerprint());
            if (repaired.userKeyPresent()) {
                return SshReadinessResponse.success();
            }
            return SshReadinessResponse.failed("AUTHORIZED_KEYS", "사용자 SSH 키 반영에 실패했습니다.");
        } catch (OpsException e) {
            return SshReadinessResponse.pending("SSH_CHECK", "SSH 준비 상태 확인을 다시 시도합니다.");
        } finally {
            session.disconnect();
        }
    }

    private Inspection inspect(Session session, String expectedFingerprint) {
        CommandResult result = sshCommandExecutor.exec(session, INSPECT_COMMAND, COMMAND_TIMEOUT_MS);
        String output = result.stdout();
        int markerIndex = output.indexOf(KEY_MARKER);
        String cloudInitOutput = markerIndex >= 0 ? output.substring(0, markerIndex) : output;
        String keyOutput = markerIndex >= 0 ? output.substring(markerIndex + KEY_MARKER.length()) : "";

        return new Inspection(
                CLOUD_INIT_DONE.matcher(cloudInitOutput).find(),
                CLOUD_INIT_FAILED.matcher(cloudInitOutput).find()
                        || cloudInitOutput.contains("cloud-init: command not found"),
                keyOutput.contains(expectedFingerprint)
        );
    }

    private void repairUserKey(Session session, String canonicalKey) {
        String encodedKey = Base64.getEncoder()
                .encodeToString(canonicalKey.getBytes(StandardCharsets.UTF_8));
        String command = "set -eu; "
                + "umask 077; "
                + "mkdir -p \"$HOME/.ssh\"; "
                + "chmod 700 \"$HOME/.ssh\"; "
                + "touch \"$HOME/.ssh/authorized_keys\"; "
                + "chmod 600 \"$HOME/.ssh/authorized_keys\"; "
                + "printf '\\n' >> \"$HOME/.ssh/authorized_keys\"; "
                + "printf '%s' '" + encodedKey + "' | base64 -d >> \"$HOME/.ssh/authorized_keys\"; "
                + "printf '\\n' >> \"$HOME/.ssh/authorized_keys\"";
        sshCommandExecutor.execOrThrow(session, command, COMMAND_TIMEOUT_MS);
    }

    private Optional<String> canonicalKey(String publicKey, String expectedFingerprint) {
        if (publicKey == null || publicKey.contains("\n") || publicKey.contains("\r")) {
            return Optional.empty();
        }

        String[] parts = publicKey.trim().split("\\s+");
        if (parts.length < 2) {
            return Optional.empty();
        }

        try {
            byte[] keyBlob = Base64.getDecoder().decode(parts[1]);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String actualFingerprint = "SHA256:" + Base64.getEncoder().withoutPadding()
                    .encodeToString(digest.digest(keyBlob));
            if (!MessageDigest.isEqual(
                    actualFingerprint.getBytes(StandardCharsets.US_ASCII),
                    expectedFingerprint.getBytes(StandardCharsets.US_ASCII))) {
                return Optional.empty();
            }
            return Optional.of(parts[0] + " " + parts[1]);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private record Inspection(
            boolean cloudInitComplete,
            boolean cloudInitFailed,
            boolean userKeyPresent
    ) {
    }
}
