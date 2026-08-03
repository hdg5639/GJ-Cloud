package gj.cloud.vm.application.ssh.dto;

public record SshKeyInternalResponse(
        String id,
        String publicKey,
        String fingerprint
) {}
