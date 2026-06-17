package gj.cloud.user.application.sshkey.dto;

import gj.cloud.user.domain.sshkey.entity.SshKeyEntity;

import java.time.LocalDateTime;

public record SshKeyResponse(
        String id,
        String name,
        String fingerprint,
        String publicKeyPreview,
        LocalDateTime createdAt
) {
    public static SshKeyResponse from(SshKeyEntity entity) {
        String key = entity.getPublicKey();
        String preview = key.length() > 30 ? key.substring(0, 30) + "..." : key;
        return new SshKeyResponse(
                entity.getId(),
                entity.getName(),
                entity.getFingerprint(),
                preview,
                entity.getCreatedAt()
        );
    }
}
