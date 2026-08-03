package gj.cloud.ops.application.backup.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;

@Component
public class BackupFileCipher {

    public static final String VERSION = "aes-256-gcm-v1";
    private static final byte[] MAGIC = "GJBAK001".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] KEY_CONTEXT = "gj-cloud-db-backup-v1".getBytes(StandardCharsets.UTF_8);
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public BackupFileCipher(
            @Value("${ops.backup.encryption-secret:${ops.management-key-encryption-secret}}") String masterSecret
    ) {
        byte[] master = masterSecret.getBytes(StandardCharsets.UTF_8);
        if (master.length != 32) {
            throw new IllegalStateException("ops.backup.encryption-secret은 UTF-8 기준 정확히 32바이트여야 합니다.");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(KEY_CONTEXT);
            this.key = new SecretKeySpec(digest.digest(master), "AES");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        } finally {
            Arrays.fill(master, (byte) 0);
        }
    }

    public EncryptionWriter encrypting(OutputStream encryptedTarget) throws IOException {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            encryptedTarget.write(MAGIC);
            encryptedTarget.write(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(MAGIC);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            OutputStream plaintext = new DigestOutputStream(new CipherOutputStream(encryptedTarget, cipher), digest);
            return new EncryptionWriter(plaintext, digest);
        } catch (GeneralSecurityException e) {
            throw new IOException("백업 암호화 초기화에 실패했습니다.", e);
        }
    }

    public String decrypt(InputStream encryptedSource, OutputStream plaintextTarget) throws IOException {
        byte[] magic = encryptedSource.readNBytes(MAGIC.length);
        byte[] iv = encryptedSource.readNBytes(IV_LENGTH);
        if (!Arrays.equals(magic, MAGIC) || iv.length != IV_LENGTH) {
            throw new IOException("백업 암호화 헤더가 올바르지 않습니다.");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(MAGIC);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            DigestOutputStream digestTarget = new DigestOutputStream(plaintextTarget, digest);
            try (CipherInputStream plaintext = new CipherInputStream(encryptedSource, cipher)) {
                plaintext.transferTo(digestTarget);
            }
            digestTarget.flush();
            return HexFormat.of().formatHex(digest.digest());
        } catch (GeneralSecurityException e) {
            throw new IOException("백업 복호화에 실패했습니다.", e);
        }
    }

    public static final class EncryptionWriter implements AutoCloseable {
        private final OutputStream plaintext;
        private final MessageDigest digest;
        private boolean closed;

        private EncryptionWriter(OutputStream plaintext, MessageDigest digest) {
            this.plaintext = plaintext;
            this.digest = digest;
        }

        public OutputStream outputStream() {
            return plaintext;
        }

        @Override
        public void close() throws IOException {
            if (!closed) {
                plaintext.close();
                closed = true;
            }
        }

        public String checksumSha256() {
            if (!closed) {
                throw new IllegalStateException("암호화 스트림을 닫은 후 체크섬을 조회해야 합니다.");
            }
            return HexFormat.of().formatHex(digest.digest());
        }
    }
}
