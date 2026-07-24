package gj.cloud.user.application.sshkey.validation;

import gj.cloud.user.global.exception.UserException;
import gj.cloud.user.global.exception.enums.UserErrorCode;
import org.bouncycastle.crypto.util.OpenSSHPublicKeyUtil;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;

@Component
public class SshPublicKeyValidator {

    private static final Set<String> SUPPORTED_KEY_TYPES = Set.of(
            "ssh-rsa", "ssh-ed25519",
            "ecdsa-sha2-nistp256", "ecdsa-sha2-nistp384", "ecdsa-sha2-nistp521"
    );

    public String validateAndNormalize(String publicKey) {
        if (publicKey == null || publicKey.isBlank() || publicKey.indexOf('\0') >= 0
                || publicKey.contains("\n") || publicKey.contains("\r")) {
            throw invalidKey();
        }

        String[] parts = publicKey.trim().split("\\s+", 3);
        if (parts.length < 2 || !SUPPORTED_KEY_TYPES.contains(parts[0])) {
            throw invalidKey();
        }

        try {
            byte[] keyBlob = Base64.getDecoder().decode(parts[1]);
            if (!parts[0].equals(readEmbeddedKeyType(keyBlob))) {
                throw invalidKey();
            }
            OpenSSHPublicKeyUtil.parsePublicKey(keyBlob);
        } catch (UserException e) {
            throw e;
        } catch (Exception e) {
            throw invalidKey();
        }

        return parts.length == 3
                ? parts[0] + " " + parts[1] + " " + parts[2].trim()
                : parts[0] + " " + parts[1];
    }

    private String readEmbeddedKeyType(byte[] keyBlob) {
        if (keyBlob.length < Integer.BYTES) {
            throw invalidKey();
        }

        ByteBuffer buffer = ByteBuffer.wrap(keyBlob);
        int typeLength = buffer.getInt();
        if (typeLength <= 0 || typeLength > buffer.remaining()) {
            throw invalidKey();
        }

        byte[] typeBytes = new byte[typeLength];
        buffer.get(typeBytes);
        return new String(typeBytes, StandardCharsets.US_ASCII);
    }

    private UserException invalidKey() {
        return new UserException(UserErrorCode.INVALID_SSH_KEY_FORMAT);
    }
}
