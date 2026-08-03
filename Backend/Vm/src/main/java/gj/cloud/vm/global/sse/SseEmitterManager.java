package gj.cloud.vm.global.sse;

import gj.cloud.vm.application.vm.dto.VmStatusEvent;
import gj.cloud.vm.global.sse.enums.SseEventType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Component
public class SseEmitterManager {

    private final ConcurrentMap<String, Sinks.Many<VmStatusEvent>> sinks = new ConcurrentHashMap<>();

    public Flux<ServerSentEvent<VmStatusEvent>> subscribe(String userId) {
        Sinks.Many<VmStatusEvent> sink = sinks.computeIfAbsent(
                userId,
                k -> Sinks.many().multicast().onBackpressureBuffer()
        );

        return sink.asFlux()
                .map(event -> ServerSentEvent.<VmStatusEvent>builder()
                        .event(SseEventType.VM_STATUS_CHANGED.name())
                        .data(event)
                        .build())
                .doOnCancel(() -> {
                    sinks.remove(userId);
                    log.debug("SSE 구독 해제: userId={}", userId);
                });
    }

    public void publish(String userId, VmStatusEvent event) {
        Sinks.Many<VmStatusEvent> sink = sinks.get(userId);
        if (sink != null) {
            sink.tryEmitNext(event);
        }
    }
}
