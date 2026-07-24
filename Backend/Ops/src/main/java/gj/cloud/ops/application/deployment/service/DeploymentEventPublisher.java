package gj.cloud.ops.application.deployment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import gj.cloud.ops.application.deployment.dto.DeploymentEventPayload;
import gj.cloud.ops.domain.deployment.entity.DeploymentEventEntity;
import gj.cloud.ops.domain.deployment.enums.DeploymentEventType;
import gj.cloud.ops.domain.deployment.repository.DeploymentEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

// D.8 — DB에 먼저 적재(재생 가능) 후 현재 연결된 SseEmitter들에 실시간 push.
// Ops는 Spring MVC(서블릿) 스택이라 WebFlux의 Sinks/Flux 대신 SseEmitter를 그대로 사용함.
@Slf4j
@Component
@RequiredArgsConstructor
public class DeploymentEventPublisher {

    private final DeploymentEventRepository repository;
    private final ObjectMapper objectMapper;

    private final Map<String, AtomicLong> sequences = new ConcurrentHashMap<>();
    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public void publish(String deploymentId, DeploymentEventType type, String message) {
        publish(deploymentId, type, message, null);
    }

    public void publish(String deploymentId, DeploymentEventType type, String message, Object payload) {
        String payloadJson = serializePayload(payload);
        long sequence = nextSequence(deploymentId);
        DeploymentEventEntity entity = DeploymentEventEntity.create(deploymentId, sequence, type.name(), message, payloadJson);
        repository.save(entity);
        broadcast(deploymentId, entity);
    }

    // 재연결 시 afterSequence 이후 누락 이벤트를 먼저 재전송한 뒤, 실시간 emitter로 등록
    public SseEmitter subscribe(String deploymentId, long afterSequence) {
        SseEmitter emitter = new SseEmitter(0L);
        List<SseEmitter> list = emitters.computeIfAbsent(deploymentId, id -> new CopyOnWriteArrayList<>());
        list.add(emitter);

        emitter.onCompletion(() -> removeEmitter(deploymentId, emitter));
        emitter.onTimeout(() -> removeEmitter(deploymentId, emitter));
        emitter.onError(e -> removeEmitter(deploymentId, emitter));

        try {
            List<DeploymentEventEntity> missed =
                    repository.findAllByDeploymentIdAndSequenceGreaterThanOrderBySequenceAsc(deploymentId, afterSequence);
            for (DeploymentEventEntity event : missed) {
                sendEvent(emitter, event);
            }
        } catch (IOException e) {
            removeEmitter(deploymentId, emitter);
        }
        return emitter;
    }

    // 배포가 종료(SUCCEEDED/FAILED/ROLLED_BACK)되면 연결된 emitter들을 정상 종료하고 시퀀스 카운터를 정리
    public void complete(String deploymentId) {
        List<SseEmitter> list = emitters.remove(deploymentId);
        if (list != null) {
            for (SseEmitter emitter : list) {
                emitter.complete();
            }
        }
        sequences.remove(deploymentId);
    }

    private void broadcast(String deploymentId, DeploymentEventEntity entity) {
        List<SseEmitter> list = emitters.get(deploymentId);
        if (list == null) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                sendEvent(emitter, entity);
            } catch (IOException e) {
                removeEmitter(deploymentId, emitter);
            }
        }
    }

    private void sendEvent(SseEmitter emitter, DeploymentEventEntity entity) throws IOException {
        emitter.send(SseEmitter.event()
                .id(String.valueOf(entity.getSequence()))
                .name(entity.getEventType())
                .data(DeploymentEventPayload.from(entity)));
    }

    private void removeEmitter(String deploymentId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(deploymentId);
        if (list != null) {
            list.remove(emitter);
        }
    }

    private long nextSequence(String deploymentId) {
        AtomicLong counter = sequences.computeIfAbsent(deploymentId,
                id -> new AtomicLong(repository.findMaxSequence(id).orElse(0L)));
        return counter.incrementAndGet();
    }

    private String serializePayload(Object payload) {
        if (payload == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("배포 이벤트 payload 직렬화 실패: {}", e.getMessage());
            return null;
        }
    }
}
