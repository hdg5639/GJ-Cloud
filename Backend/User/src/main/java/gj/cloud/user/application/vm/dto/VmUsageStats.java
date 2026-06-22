package gj.cloud.user.application.vm.dto;

public record VmUsageStats(
        long myFreeCount,
        long myProCount,
        long systemFreeCount,
        long systemProCount
) {
    public static VmUsageStats empty() {
        return new VmUsageStats(0, 0, 0, 0);
    }
}
