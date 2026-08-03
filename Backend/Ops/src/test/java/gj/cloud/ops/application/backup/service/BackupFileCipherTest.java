package gj.cloud.ops.application.backup.service;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BackupFileCipherTest {

    private final BackupFileCipher cipher = new BackupFileCipher("0123456789abcdef0123456789abcdef");

    @Test
    void encryptsAndDecryptsWithMatchingPlaintextChecksum() throws Exception {
        byte[] plaintext = "CREATE TABLE example(id bigint);\n".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream encrypted = new ByteArrayOutputStream();
        BackupFileCipher.EncryptionWriter writer = cipher.encrypting(encrypted);
        try (writer) {
            writer.outputStream().write(plaintext);
        }

        ByteArrayOutputStream restored = new ByteArrayOutputStream();
        String restoredChecksum = cipher.decrypt(
                new ByteArrayInputStream(encrypted.toByteArray()), restored);

        assertThat(restored.toByteArray()).isEqualTo(plaintext);
        assertThat(restoredChecksum).isEqualTo(writer.checksumSha256());
        assertThat(new String(encrypted.toByteArray(), StandardCharsets.ISO_8859_1))
                .doesNotContain(new String(plaintext, StandardCharsets.ISO_8859_1));
    }

    @Test
    void rejectsTamperedCiphertext() throws Exception {
        ByteArrayOutputStream encrypted = new ByteArrayOutputStream();
        try (BackupFileCipher.EncryptionWriter writer = cipher.encrypting(encrypted)) {
            writer.outputStream().write("backup".getBytes(StandardCharsets.UTF_8));
        }
        byte[] tampered = encrypted.toByteArray();
        tampered[tampered.length - 1] ^= 1;

        assertThatThrownBy(() -> cipher.decrypt(
                new ByteArrayInputStream(tampered), new ByteArrayOutputStream()))
                .isInstanceOf(IOException.class);
    }
}
