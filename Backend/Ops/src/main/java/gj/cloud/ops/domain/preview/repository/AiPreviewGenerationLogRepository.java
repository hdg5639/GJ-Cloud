package gj.cloud.ops.domain.preview.repository;

import gj.cloud.ops.domain.preview.entity.AiPreviewGenerationLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiPreviewGenerationLogRepository extends JpaRepository<AiPreviewGenerationLogEntity, String> {
}
