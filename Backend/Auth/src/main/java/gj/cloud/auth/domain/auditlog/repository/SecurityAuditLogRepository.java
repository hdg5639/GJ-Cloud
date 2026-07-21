package gj.cloud.auth.domain.auditlog.repository;

import gj.cloud.auth.domain.auditlog.entity.SecurityAuditLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecurityAuditLogRepository extends JpaRepository<SecurityAuditLogEntity, String> {
    Page<SecurityAuditLogEntity> findAllByOrderByOccurredAtDesc(Pageable pageable);

    Page<SecurityAuditLogEntity> findAllByActorIdOrderByOccurredAtDesc(String actorId, Pageable pageable);
}
