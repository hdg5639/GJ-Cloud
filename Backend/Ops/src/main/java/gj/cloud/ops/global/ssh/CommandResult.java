package gj.cloud.ops.global.ssh;

public record CommandResult(int exitStatus, String stdout, String stderr) {
    public boolean isSuccess() {
        return exitStatus == 0;
    }
}
