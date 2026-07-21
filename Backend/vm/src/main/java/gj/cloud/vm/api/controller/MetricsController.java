package gj.cloud.vm.api.controller;

import gj.cloud.vm.application.vm.dto.VmMetricsCurrentResponse;
import gj.cloud.vm.application.vm.service.VmService;
import gj.cloud.vm.global.response.ApiResponse;
import gj.cloud.vm.global.security.VmPrincipal;
import gj.cloud.vm.global.sse.SseTicketResponse;
import gj.cloud.vm.global.sse.SseTicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Tag(name = "Metrics")
@RestController
@RequestMapping("/vms/{vmId}/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final VmService vmService;
    private final SseTicketService sseTicketService;

    // SEC-006: subscribe와 동일하게 1회용 티켓 발급 후 그 티켓으로만 스트림 구독.
    @Operation(summary = "메트릭 SSE 구독용 1회용 티켓 발급", description = "30초 내 미사용 시 만료, 1회 사용 후 즉시 폐기")
    @PostMapping("/ticket")
    public Mono<ApiResponse<SseTicketResponse>> issueTicket(@AuthenticationPrincipal VmPrincipal principal) {
        return sseTicketService.issueTicket(principal.userId(), principal.email())
                .map(ticket -> ApiResponse.ok(new SseTicketResponse(ticket)));
    }

    @Operation(summary = "VM 메트릭 실시간 스트림 (SSE)")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<VmMetricsCurrentResponse>> streamMetrics(
            @AuthenticationPrincipal VmPrincipal principal,
            @PathVariable UUID vmId
    ) {
        log.info("메트릭 SSE 구독: userId={}, vmId={}", principal.userId(), vmId);

        return Flux.interval(Duration.ofSeconds(5))
                .flatMap(tick -> vmService.getVmMetricsCurrent(principal.userId(), principal.email(), vmId)
                        .doOnSuccess(metrics -> log.debug("메트릭 발행: vmId={}, cpu={}", vmId, metrics.cpuUsagePercent()))
                        .doOnError(e -> log.warn("메트릭 조회 실패: vmId={}, error={}", vmId, e.getMessage())))
                .map(metrics -> ServerSentEvent.<VmMetricsCurrentResponse>builder()
                        .data(metrics)
                        .build())
                .doOnCancel(() -> log.info("메트릭 SSE 구독 해제: vmId={}", vmId))
                .onErrorResume(e -> {
                    log.warn("메트릭 스트림 종료: vmId={}, error={}", vmId, e.getMessage());
                    return Flux.empty();
                });
    }
}
