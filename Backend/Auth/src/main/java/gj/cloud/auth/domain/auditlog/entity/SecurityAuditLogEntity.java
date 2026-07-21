package gj.cloud.auth.domain.auditlog.entity;

import gj.cloud.auth.domain.auditlog.enums.AuditAction;
import gj.cloud.auth.domain.auditlog.enums.AuditActorType;
import gj.cloud.auth.domain.auditlog.enums.AuditResult;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

// OBS-001: 고위험 보안 이벤트 감사로그. 불변 레코드 — 생성 후 수정하지 않음(감사 무결성).
@Entity
@Table(name = "security_audit_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class SecurityAuditLogEntity {

    @Id
    private String id;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false)
    private AuditActorType actorType;

    @Column(name = "actor_id")
    private String actorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditAction action;

    @Column(name = "target_type")
    private String targetType;

    @Column(name = "target_id")
    private String targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditResult result;

    private String ip;

    @Column(name = "correlation_id")
    private String correlationId;

    // 시크릿/토큰 원문은 절대 넣지 않음 — 사유 코드/짧은 설명만(SecurityAuditLogService 호출부 책임)
    private String reason;

    public static SecurityAuditLogEntity create(AuditActorType actorType, String actorId, AuditAction action,
                                                 String targetType, String targetId, AuditResult result,
                                                 String ip, String correlationId, String reason) {
        return SecurityAuditLogEntity.builder()
                .id(UUID.randomUUID().toString())
                .occurredAt(LocalDateTime.now())
                .actorType(actorType)
                .actorId(actorId)
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .result(result)
                .ip(ip)
                .correlationId(correlationId)
                .reason(reason)
                .build();
    }
}
