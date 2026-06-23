package gj.cloud.vm.application.vm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record VmMetricsHistoryResponse(
        String vmId,
        String timeframe,
        List<MetricDataPoint> dataPoints
) {
    public record MetricDataPoint(
            long timestamp,
            @JsonProperty("cpu")
            Double cpuPercent,
            @JsonProperty("mem")
            Long memoryUsedBytes,
            @JsonProperty("netIn")
            Long networkInBytes,
            @JsonProperty("netOut")
            Long networkOutBytes,
            @JsonProperty("diskRead")
            Long diskReadBytes,
            @JsonProperty("diskWrite")
            Long diskWriteBytes
    ) {}
}
