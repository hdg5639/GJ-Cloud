package gj.cloud.ops.application.sshreadiness.service;

import com.jcraft.jsch.Session;
import gj.cloud.ops.application.sshreadiness.dto.SshReadinessRequest;
import gj.cloud.ops.application.sshreadiness.dto.SshReadinessResponse;
import gj.cloud.ops.global.ssh.CommandResult;
import gj.cloud.ops.global.ssh.SshCommandExecutor;
import gj.cloud.ops.global.ssh.VmSshSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VmSshReadinessServiceTest {

    private static final String VM_ID = "vm-1";
    private static final String IP = "192.168.0.100";

    @Mock
    private VmSshSessionFactory sshSessionFactory;
    @Mock
    private SshCommandExecutor sshCommandExecutor;
    @Mock
    private Session session;

    private VmSshReadinessService service;
    private SshReadinessRequest request;

    @BeforeEach
    void setUp() throws Exception {
        service = new VmSshReadinessService(sshSessionFactory, sshCommandExecutor);
        String publicKey = validPublicKey();
        request = new SshReadinessRequest(IP, publicKey, fingerprint(publicKey));
    }

    @Test
    void staysPendingUntilManagementKeyAuthenticationSucceeds() {
        when(sshSessionFactory.tryCreateSession(VM_ID, IP)).thenReturn(Optional.empty());

        SshReadinessResponse response = service.ensureReady(VM_ID, request);

        assertThat(response.ready()).isFalse();
        assertThat(response.terminal()).isFalse();
        assertThat(response.stage()).isEqualTo("SSH_CONNECTING");
    }

    @Test
    void reportsReadyAfterCloudInitAndUserKeyAreVerified() {
        when(sshSessionFactory.tryCreateSession(VM_ID, IP)).thenReturn(Optional.of(session));
        when(sshCommandExecutor.exec(eq(session), contains("cloud-init status"), anyLong()))
                .thenReturn(inspection("status: done", request.expectedUserKeyFingerprint()));

        SshReadinessResponse response = service.ensureReady(VM_ID, request);

        assertThat(response.ready()).isTrue();
        verify(session).disconnect();
    }

    @Test
    void repairsMissingUserKeyWithTheAuthenticatedManagementSession() {
        when(sshSessionFactory.tryCreateSession(VM_ID, IP)).thenReturn(Optional.of(session));
        when(sshCommandExecutor.exec(eq(session), contains("cloud-init status"), anyLong()))
                .thenReturn(
                        inspection("status: done", ""),
                        inspection("status: done", request.expectedUserKeyFingerprint())
                );
        when(sshCommandExecutor.execOrThrow(eq(session), contains("authorized_keys"), anyLong()))
                .thenReturn(new CommandResult(0, "", ""));

        SshReadinessResponse response = service.ensureReady(VM_ID, request);

        assertThat(response.ready()).isTrue();
        verify(sshCommandExecutor).execOrThrow(eq(session), contains("base64 -d"), anyLong());
    }

    @Test
    void rejectsPublicKeyAndFingerprintMismatchWithoutConnecting() {
        SshReadinessRequest mismatched = new SshReadinessRequest(
                IP, request.expectedUserPublicKey(), "SHA256:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");

        SshReadinessResponse response = service.ensureReady(VM_ID, mismatched);

        assertThat(response.terminal()).isTrue();
        assertThat(response.stage()).isEqualTo("KEY_VALIDATION");
    }

    private CommandResult inspection(String status, String fingerprint) {
        String stdout = status + "\n\n__GAMJABOX_AUTHORIZED_KEYS__\n"
                + (fingerprint.isBlank() ? "" : "256 " + fingerprint + " user (ED25519)\n");
        return new CommandResult(0, stdout, "");
    }

    private String validPublicKey() {
        byte[] type = "ssh-ed25519".getBytes(StandardCharsets.US_ASCII);
        byte[] key = new byte[32];
        ByteBuffer buffer = ByteBuffer.allocate(4 + type.length + 4 + key.length);
        buffer.putInt(type.length).put(type).putInt(key.length).put(key);
        return "ssh-ed25519 " + Base64.getEncoder().encodeToString(buffer.array());
    }

    private String fingerprint(String publicKey) throws Exception {
        byte[] blob = Base64.getDecoder().decode(publicKey.split("\\s+")[1]);
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(blob);
        return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest);
    }
}
