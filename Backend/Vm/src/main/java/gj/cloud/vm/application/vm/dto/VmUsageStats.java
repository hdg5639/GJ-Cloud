package gj.cloud.vm.application.vm.dto;

public record VmUsageStats(
        long myFreeCount,
        long myProCount,
        long systemFreeCount,
        long systemProCount
) {}
