package gj.cloud.ops.application.sshreadiness.dto;

public record SshReadinessResponse(
        boolean ready,
        boolean terminal,
        String stage,
        String detail
) {
    public static SshReadinessResponse success() {
        return new SshReadinessResponse(true, false, "READY", null);
    }

    public static SshReadinessResponse pending(String stage, String detail) {
        return new SshReadinessResponse(false, false, stage, detail);
    }

    public static SshReadinessResponse failed(String stage, String detail) {
        return new SshReadinessResponse(false, true, stage, detail);
    }
}
