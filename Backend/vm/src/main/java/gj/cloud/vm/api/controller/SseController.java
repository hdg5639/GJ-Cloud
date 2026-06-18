package gj.cloud.vm.api.controller;

import gj.cloud.vm.application.vm.dto.VmStatusEvent;
import gj.cloud.vm.global.security.VmPrincipal;
import gj.cloud.vm.global.sse.SseEmitterManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/vms/events")
@RequiredArgsConstructor
public class SseController {

    private final SseEmitterManager sseEmitterManager;

    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<VmStatusEvent>> subscribe(@AuthenticationPrincipal VmPrincipal principal) {
        return sseEmitterManager.subscribe(principal.userId());
    }
}
