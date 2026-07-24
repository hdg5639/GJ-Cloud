package gj.cloud.ops.global.process;

public record LocalCommandResult(int exitStatus, String stdout, String stderr) {
    public boolean isSuccess() {
        return exitStatus == 0;
    }
}
