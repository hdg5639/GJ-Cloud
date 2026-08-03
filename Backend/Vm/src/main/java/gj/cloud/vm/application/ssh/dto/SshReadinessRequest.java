package gj.cloud.vm.application.ssh.dto;

public record SshReadinessRequest(
        String internalIp,
        String expectedUserPublicKey,
        String expectedUserKeyFingerprint
) {
}
