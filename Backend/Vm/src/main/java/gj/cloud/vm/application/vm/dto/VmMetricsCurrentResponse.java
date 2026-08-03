package gj.cloud.vm.application.vm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record VmMetricsCurrentResponse(
        String vmId,
        String status,
        @JsonProperty(value = "cpuUsagePercent")
        BigDecimal cpuUsagePercent,
        @JsonProperty(value = "allocatedCpu")
        int allocatedCpu,
        @JsonProperty(value = "memoryUsedBytes")
        long memoryUsedBytes,
        @JsonProperty(value = "memoryAllocatedBytes")
        long memoryAllocatedBytes,
        @JsonProperty(value = "diskUsedBytes")
        long diskUsedBytes,
        @JsonProperty(value = "diskAllocatedBytes")
        long diskAllocatedBytes,
        @JsonProperty(value = "networkInBytes")
        long networkInBytes,
        @JsonProperty(value = "networkOutBytes")
        long networkOutBytes,
        @JsonProperty(value = "uptimeSeconds")
        long uptimeSeconds,
        long timestamp
) {}
