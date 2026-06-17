package gj.cloud.user.application.usage.dto;

public record UsageResponse(
        String planType,
        int vCpuLimit,
        int ramGbLimit,
        int currentVmCount,
        int maxVmCount
) {}
