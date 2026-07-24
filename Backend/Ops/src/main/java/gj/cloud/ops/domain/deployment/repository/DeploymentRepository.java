package gj.cloud.ops.domain.deployment.repository;

import gj.cloud.ops.domain.deployment.entity.DeploymentEntity;
import gj.cloud.ops.domain.deployment.enums.DeploymentStatus;
import gj.cloud.ops.domain.deployment.enums.DeploymentTriggerType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DeploymentRepository extends JpaRepository<DeploymentEntity, String> {

    List<DeploymentEntity> findAllByVmIdOrderByCreatedAtDesc(String vmId);

    List<DeploymentEntity> findAllByStatus(DeploymentStatus status);

    Optional<DeploymentEntity> findTopByVmIdAndDeploymentTargetIdIsNullAndStatusOrderByCreatedAtDesc(
            String vmId, DeploymentStatus status);

    boolean existsByDeploymentTargetIdAndRequestedRevisionAndCreatedAtGreaterThanEqual(
            String deploymentTargetId, String requestedRevision, LocalDateTime requestedAt);

    Optional<DeploymentEntity> findTopByDeploymentTargetIdOrderByCreatedAtDesc(String deploymentTargetId);

    List<DeploymentEntity> findAllByTriggerTypeAndStatusNotIn(
            DeploymentTriggerType triggerType, List<DeploymentStatus> terminalStatuses);

}
