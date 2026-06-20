package gj.cloud.user.application.sshkey.dto;

import gj.cloud.user.domain.sshkey.entity.SshKeyEntity;

import java.time.LocalDateTime;

public record SshKeyGenerateResponse(
        String id,
        String name,
        String fingerprint,
        String publicKeyPreview,
        String privateKey,
        LocalDateTime createdAt
) {
    public static SshKeyGenerateResponse of(SshKeyEntity entity, String privateKey) {
        String key = entity.getPublicKey();
        String preview = key.length() > 30 ? key.substring(0, 30) + "..." : key;
        return new SshKeyGenerateResponse(
                entity.getId(),
                entity.getName(),
                entity.getFingerprint(),
                preview,
                privateKey,
                entity.getCreatedAt()
        );
    }
}
