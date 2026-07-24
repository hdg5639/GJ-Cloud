package gj.cloud.auth.api.controller.dto;

import gj.cloud.auth.domain.auditlog.entity.SecurityAuditLogEntity;
import gj.cloud.auth.domain.auditlog.enums.AuditAction;
import gj.cloud.auth.domain.auditlog.enums.AuditActorType;
import gj.cloud.auth.domain.auditlog.enums.AuditResult;

import java.time.LocalDateTime;

public record SecurityAuditLogResponse(
        String id,
        LocalDateTime occurredAt,
        AuditActorType actorType,
        String actorId,
        AuditAction action,
        String targetType,
        String targetId,
        AuditResult result,
        String ip,
        String correlationId,
        String reason
) {
    public static SecurityAuditLogResponse from(SecurityAuditLogEntity entity) {
        return new SecurityAuditLogResponse(
                entity.getId(),
                entity.getOccurredAt(),
                entity.getActorType(),
                entity.getActorId(),
                entity.getAction(),
                entity.getTargetType(),
                entity.getTargetId(),
                entity.getResult(),
                entity.getIp(),
                entity.getCorrelationId(),
                entity.getReason()
        );
    }
}
