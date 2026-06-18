package gj.cloud.vm.api.controller;

import gj.cloud.vm.application.vm.dto.VmStatusEvent;
import gj.cloud.vm.global.security.VmPrincipal;
import gj.cloud.vm.global.sse.SseEmitterManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@Tag(name = "VM 이벤트", description = "VM 상태 변경 실시간 스트림 (SSE)")
@RestController
@RequestMapping("/vms/events")
@RequiredArgsConstructor
public class SseController {

    private final SseEmitterManager sseEmitterManager;

    @Operation(summary = "VM 상태 이벤트 구독", description = "VM 생성·전원 변경 시 상태를 실시간으로 수신합니다. status: PENDING→CREATING→BOOTING→RUNNING / STARTING→RUNNING / STOPPING→STOPPED / SUSPENDING→SUSPENDED / FAILED")
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<VmStatusEvent>> subscribe(@AuthenticationPrincipal VmPrincipal principal) {
        return sseEmitterManager.subscribe(principal.userId());
    }
}
