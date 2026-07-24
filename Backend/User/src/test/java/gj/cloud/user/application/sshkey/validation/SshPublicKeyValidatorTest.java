package gj.cloud.user.application.sshkey.validation;

import gj.cloud.user.global.exception.UserException;
import org.junit.jupiter.api.Test;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.util.OpenSSHPublicKeyUtil;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SshPublicKeyValidatorTest {

    private final SshPublicKeyValidator validator = new SshPublicKeyValidator();

    @Test
    void acceptsAndNormalizesAValidEd25519Key() {
        String encoded = validEd25519Blob();

        String normalized = validator.validateAndNormalize(
                "  ssh-ed25519   " + encoded + "   developer@example.com  ");

        assertThat(normalized).isEqualTo("ssh-ed25519 " + encoded + " developer@example.com");
    }

    @Test
    void rejectsWhenDeclaredAndEmbeddedAlgorithmsDiffer() {
        assertThatThrownBy(() -> validator.validateAndNormalize("ssh-rsa " + validEd25519Blob()))
                .isInstanceOf(UserException.class);
    }

    @Test
    void rejectsMalformedOpenSshKeyBlob() {
        String malformed = Base64.getEncoder().encodeToString("not-an-openssh-key".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> validator.validateAndNormalize("ssh-ed25519 " + malformed))
                .isInstanceOf(UserException.class);
    }

    @Test
    void rejectsMultipleKeysInOneRegistration() {
        String key = "ssh-ed25519 " + validEd25519Blob();

        assertThatThrownBy(() -> validator.validateAndNormalize(key + "\n" + key))
                .isInstanceOf(UserException.class);
    }

    private String validEd25519Blob() {
        try {
            byte[] privateKey = new byte[32];
            for (int i = 0; i < privateKey.length; i++) {
                privateKey[i] = (byte) (i + 1);
            }
            Ed25519PrivateKeyParameters parameters = new Ed25519PrivateKeyParameters(privateKey);
            byte[] encoded = OpenSSHPublicKeyUtil.encodePublicKey(parameters.generatePublicKey());
            return Base64.getEncoder().encodeToString(encoded);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
