package gj.cloud.ops.domain.deployment.repository;

import gj.cloud.ops.domain.deployment.entity.AiSpecGenerationLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiSpecGenerationLogRepository extends JpaRepository<AiSpecGenerationLogEntity, String> {
}
