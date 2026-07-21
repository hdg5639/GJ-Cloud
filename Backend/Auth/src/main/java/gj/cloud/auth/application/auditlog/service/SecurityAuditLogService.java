package gj.cloud.auth.application.auditlog.service;

import gj.cloud.auth.domain.auditlog.entity.SecurityAuditLogEntity;
import gj.cloud.auth.domain.auditlog.enums.AuditAction;
import gj.cloud.auth.domain.auditlog.enums.AuditActorType;
import gj.cloud.auth.domain.auditlog.enums.AuditResult;
import gj.cloud.auth.domain.auditlog.repository.SecurityAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// OBS-001: 로그인/refresh 재사용 탈취/계정 정지·복구·탈퇴처럼 고위험으로 분류된 이벤트만 기록.
// 기록 자체가 실패해도(DB 문제 등) 원래 요청 흐름을 막지 않는다 — 감사로그 부재가 서비스 장애로
// 번지면 안 됨.
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityAuditLogService {

    private final SecurityAuditLogRepository repository;

    public void record(AuditActorType actorType, String actorId, AuditAction action,
                        String targetType, String targetId, AuditResult result,
                        String ip, String reason) {
        try {
            String correlationId = UUID.randomUUID().toString();
            repository.save(SecurityAuditLogEntity.create(
                    actorType, actorId, action, targetType, targetId, result, ip, correlationId, reason));
        } catch (Exception e) {
            log.warn("감사로그 기록 실패(요청 흐름은 계속 진행): action={}, actorId={}, error={}",
                    action, actorId, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Page<SecurityAuditLogEntity> list(String actorId, Pageable pageable) {
        return actorId != null && !actorId.isBlank()
                ? repository.findAllByActorIdOrderByOccurredAtDesc(actorId, pageable)
                : repository.findAllByOrderByOccurredAtDesc(pageable);
    }
}
