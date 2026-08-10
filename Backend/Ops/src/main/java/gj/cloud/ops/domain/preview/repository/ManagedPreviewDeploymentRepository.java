package gj.cloud.ops.domain.preview.repository;

import gj.cloud.ops.domain.preview.entity.ManagedPreviewDeploymentEntity;
import gj.cloud.ops.domain.preview.enums.ManagedPreviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ManagedPreviewDeploymentRepository extends JpaRepository<ManagedPreviewDeploymentEntity, String> {
    boolean existsByDeploymentTargetId(String deploymentTargetId);
    Optional<ManagedPreviewDeploymentEntity> findByIdAndUserId(String id, String userId);
    List<ManagedPreviewDeploymentEntity> findAllByUserIdOrderByCreatedAtDesc(String userId);
    List<ManagedPreviewDeploymentEntity> findAllByStatusIn(Collection<ManagedPreviewStatus> statuses);
    List<ManagedPreviewDeploymentEntity> findAllByExpiresAtBeforeAndStatusIn(LocalDateTime now, Collection<ManagedPreviewStatus> statuses);
}
