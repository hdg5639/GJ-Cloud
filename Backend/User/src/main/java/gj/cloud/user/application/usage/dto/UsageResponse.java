package gj.cloud.user.application.usage.dto;

public record UsageResponse(
        String planType,
        int vCpuLimit,
        int ramGbLimit,
        int myFreeCount,
        int maxFreeVmCount,
        int myProCount,
        int maxProVmCount,
        long systemFreeCount,
        long systemProCount
) {}
