package gj.cloud.vm.application.ssh.dto;

public record SshReadinessResponse(
        boolean ready,
        boolean terminal,
        String stage,
        String detail
) {
}
