package gj.cloud.vm.api.controller;

import gj.cloud.vm.application.vm.dto.VmMetricsCurrentResponse;
import gj.cloud.vm.application.vm.service.VmService;
import gj.cloud.vm.global.jwt.JwtValidator;
import gj.cloud.vm.global.response.ApiResponse;
import gj.cloud.vm.global.security.VmPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Tag(name = "Metrics")
@RestController
@RequestMapping("/vms/{vmId}/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final VmService vmService;
    private final JwtValidator jwtValidator;

    @Operation(summary = "VM 메트릭 실시간 스트림 (SSE)")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamMetrics(
            @AuthenticationPrincipal VmPrincipal principal,
            @PathVariable UUID vmId,
            @RequestParam(required = false) String token
    ) {
        Flux<String> result;

        if (principal != null) {
            result = createMetricsStream(principal.userId(), vmId);
        } else if (token != null) {
            result = jwtValidator.validate(token)
                    .flatMapMany(claims -> {
                        try {
                            String userId = claims.getStringClaim("sub");
                            return createMetricsStream(userId, vmId);
                        } catch (Exception e) {
                            return Flux.error(new IllegalArgumentException("Invalid token claims"));
                        }
                    })
                    .onErrorResume(e -> {
                        log.warn("SSE 토큰 검증 실패: {}", e.getMessage());
                        return Flux.error(new IllegalArgumentException("Unauthorized"));
                    });
        } else {
            result = Flux.error(new IllegalArgumentException("Unauthorized"));
        }

        return result;
    }

    private Flux<String> createMetricsStream(String userId, UUID vmId) {
        return Flux.interval(Duration.ofSeconds(5))
                .flatMap(tick -> vmService.getVmMetricsCurrent(userId, vmId)
                        .map(this::formatSSE)
                        .onErrorReturn("data: {\"error\": \"메트릭 조회 실패\"}\n\n")
                );
    }

    private String formatSSE(VmMetricsCurrentResponse metrics) {
        return "data: " + toJson(metrics) + "\n\n";
    }

    private String toJson(VmMetricsCurrentResponse metrics) {
        return String.format(
                "{\"vmId\":\"%s\",\"cpuUsagePercent\":%.4f,\"allocatedCpu\":%d,\"memoryUsedBytes\":%d,\"memoryAllocatedBytes\":%d,\"diskUsedBytes\":%d,\"diskAllocatedBytes\":%d,\"networkInBytes\":%d,\"networkOutBytes\":%d,\"uptimeSeconds\":%d,\"status\":\"%s\",\"timestamp\":%d}",
                metrics.vmId(),
                metrics.cpuUsagePercent(),
                metrics.allocatedCpu(),
                metrics.memoryUsedBytes(),
                metrics.memoryAllocatedBytes(),
                metrics.diskUsedBytes(),
                metrics.diskAllocatedBytes(),
                metrics.networkInBytes(),
                metrics.networkOutBytes(),
                metrics.uptimeSeconds(),
                metrics.status(),
                metrics.timestamp()
        );
    }
}
