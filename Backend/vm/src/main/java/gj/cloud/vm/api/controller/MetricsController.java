package gj.cloud.vm.api.controller;

import gj.cloud.vm.application.vm.dto.VmMetricsCurrentResponse;
import gj.cloud.vm.application.vm.service.VmService;
import gj.cloud.vm.global.security.VmPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @Operation(summary = "VM 메트릭 실시간 스트림 (SSE)")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<VmMetricsCurrentResponse>> streamMetrics(
            @AuthenticationPrincipal VmPrincipal principal,
            @PathVariable UUID vmId
    ) {
        return Flux.interval(Duration.ofSeconds(5))
                .flatMap(tick -> vmService.getVmMetricsCurrent(principal.userId(), vmId))
                .map(metrics -> ServerSentEvent.<VmMetricsCurrentResponse>builder()
                        .data(metrics)
                        .build())
                .onErrorResume(e -> {
                    log.error("메트릭 스트림 오류: vmId={}, error={}", vmId, e.getMessage());
                    return Flux.empty();
                });
    }
}
