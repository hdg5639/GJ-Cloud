package gj.cloud.ops.domain.deployment.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

// D.8 — VM 서비스의 인메모리 SseEmitterManager와 달리 DB에 먼저 적재해 재생 가능하게 함.
// Ops 재시작으로 실시간 sink가 끊겨도, 재연결 시 sequence 기준으로 누락분을 그대로 복구할 수 있음.
@Entity
@Table(name = "deployment_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class DeploymentEventEntity {

    @Id
    private String id;

    @Column(name = "deployment_id", nullable = false)
    private String deploymentId;

    @Column(nullable = false)
    private long sequence;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static DeploymentEventEntity create(String deploymentId, long sequence, String eventType,
                                                String message, String payload) {
        return DeploymentEventEntity.builder()
                .id(UUID.randomUUID().toString())
                .deploymentId(deploymentId)
                .sequence(sequence)
                .eventType(eventType)
                .message(message)
                .payload(payload)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
