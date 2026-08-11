package gj.cloud.ops.domain.preview.repository;

import gj.cloud.ops.domain.preview.entity.ManagedPreviewDeploymentEntity;
import gj.cloud.ops.domain.preview.enums.ManagedPreviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ManagedPreviewDeploymentRepository extends JpaRepository<ManagedPreviewDeploymentEntity, String> {
    boolean existsByDeploymentTargetId(String deploymentTargetId);
    Optional<ManagedPreviewDeploymentEntity> findByIdAndUserId(String id, String userId);
    List<ManagedPreviewDeploymentEntity> findAllByUserIdOrderByCreatedAtDesc(String userId);
    List<ManagedPreviewDeploymentEntity> findAllByExpiresAtBeforeAndStatusIn(LocalDateTime now, Collection<ManagedPreviewStatus> statuses);

    @Query(value = """
            SELECT candidate
              FROM generate_series(:portStart, :portEnd) AS candidate
             WHERE NOT EXISTS (
                   SELECT 1
                     FROM managed_preview_deployments preview
                    WHERE preview.internal_port = candidate
                      AND preview.status IN ('ALLOCATED','QUEUED','BUILDING','RUNNING','FAILED')
             )
             ORDER BY candidate
             LIMIT 1
            """, nativeQuery = true)
    Optional<Integer> findFirstAvailablePort(
            @Param("portStart") int portStart,
            @Param("portEnd") int portEnd);
}
