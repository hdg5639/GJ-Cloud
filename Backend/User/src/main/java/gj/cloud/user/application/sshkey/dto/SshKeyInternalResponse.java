package gj.cloud.user.application.sshkey.dto;

import gj.cloud.user.domain.sshkey.entity.SshKeyEntity;

public record SshKeyInternalResponse(
        String id,
        String publicKey,
        String fingerprint
) {
    public static SshKeyInternalResponse from(SshKeyEntity entity) {
        return new SshKeyInternalResponse(
                entity.getId(),
                entity.getPublicKey(),
                entity.getFingerprint()
        );
    }
}
