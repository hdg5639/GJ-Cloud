package gj.cloud.ops.global.crypto;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

// Ed25519 관리 키페어 생성. JSch 자신의 KeyPair 유틸로 생성/직렬화해 이후 addIdentity()로
// 그대로 로드 가능함을 보장함 (직접 OpenSSH 바이너리 포맷을 구현하지 않음 — 포맷 불일치 위험 제거).
@Component
public class ManagementSshKeyGenerator {

    private static final String COMMENT = "gamjabox-ops-managed-key";

    public ManagementKeyPair generate() {
        try {
            JSch jsch = new JSch();
            KeyPair kpair = KeyPair.genKeyPair(jsch, KeyPair.ED25519);

            ByteArrayOutputStream privOut = new ByteArrayOutputStream();
            kpair.writePrivateKey(privOut);

            ByteArrayOutputStream pubOut = new ByteArrayOutputStream();
            kpair.writePublicKey(pubOut, COMMENT);

            kpair.dispose();

            String publicKeyLine = pubOut.toString(StandardCharsets.UTF_8).trim();
            return new ManagementKeyPair(publicKeyLine, privOut.toByteArray());
        } catch (JSchException e) {
            throw new IllegalStateException("SSH 관리 키페어 생성 실패", e);
        }
    }
}
