package gj.cloud.vm.api.controller;

import gj.cloud.vm.application.vm.dto.VmStatusEvent;
import gj.cloud.vm.global.response.ApiResponse;
import gj.cloud.vm.global.security.VmPrincipal;
import gj.cloud.vm.global.sse.SseEmitterManager;
import gj.cloud.vm.global.sse.SseTicketResponse;
import gj.cloud.vm.global.sse.SseTicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Tag(name = "VM 이벤트", description = "VM 상태 변경 실시간 스트림 (SSE)")
@RestController
@RequestMapping("/vms/events")
@RequiredArgsConstructor
public class SseController {

    private final SseEmitterManager sseEmitterManager;
    private final SseTicketService sseTicketService;

    // SEC-006: EventSource는 Authorization 헤더를 못 보내므로, 정상 인증된 요청으로 먼저 1회용 티켓을
    // 발급받고 subscribe는 그 티켓으로만 연결한다(원본 액세스 토큰이 URL에 노출되지 않도록).
    @Operation(summary = "SSE 구독용 1회용 티켓 발급", description = "30초 내 미사용 시 만료, 1회 사용 후 즉시 폐기")
    @PostMapping("/ticket")
    public Mono<ApiResponse<SseTicketResponse>> issueTicket(@AuthenticationPrincipal VmPrincipal principal) {
        return sseTicketService.issueTicket(principal.userId(), principal.email())
                .map(ticket -> ApiResponse.ok(new SseTicketResponse(ticket)));
    }

    @Operation(summary = "VM 상태 이벤트 구독", description = "VM 생성·전원 변경 시 상태를 실시간으로 수신합니다. status: PENDING→CREATING→BOOTING→RUNNING / STARTING→RUNNING / STOPPING→STOPPED / SUSPENDING→SUSPENDED / FAILED")
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<VmStatusEvent>> subscribe(@AuthenticationPrincipal VmPrincipal principal) {
        return sseEmitterManager.subscribe(principal.userId());
    }
}
