package gj.cloud.ops.application.deployment.dto;

import gj.cloud.ops.domain.deployment.entity.DeploymentEventEntity;

import java.time.LocalDateTime;

public record DeploymentEventPayload(
        long sequence,
        String eventType,
        String message,
        String payload,
        LocalDateTime createdAt
) {
    public static DeploymentEventPayload from(DeploymentEventEntity entity) {
        return new DeploymentEventPayload(
                entity.getSequence(),
                entity.getEventType(),
                entity.getMessage(),
                entity.getPayload(),
                entity.getCreatedAt()
        );
    }
}
