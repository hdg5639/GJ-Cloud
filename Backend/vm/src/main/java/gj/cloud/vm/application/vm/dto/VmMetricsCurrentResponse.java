package gj.cloud.vm.application.vm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record VmMetricsCurrentResponse(
        String vmId,
        String status,
        @JsonProperty("cpu")
        BigDecimal cpuUsagePercent,
        @JsonProperty("maxCpu")
        int allocatedCpu,
        @JsonProperty("mem")
        long memoryUsedBytes,
        @JsonProperty("maxMem")
        long memoryAllocatedBytes,
        @JsonProperty("diskUsedBytes")
        long diskUsedBytes,
        @JsonProperty("diskAllocatedBytes")
        long diskAllocatedBytes,
        @JsonProperty("netIn")
        long networkInBytes,
        @JsonProperty("netOut")
        long networkOutBytes,
        @JsonProperty("uptime")
        long uptimeSeconds,
        long timestamp
) {}
