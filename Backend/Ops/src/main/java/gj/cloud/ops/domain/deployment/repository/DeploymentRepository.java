package gj.cloud.ops.domain.deployment.repository;

import gj.cloud.ops.domain.deployment.entity.DeploymentEntity;
import gj.cloud.ops.domain.deployment.enums.DeploymentStatus;
import gj.cloud.ops.domain.deployment.enums.DeploymentTriggerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DeploymentRepository extends JpaRepository<DeploymentEntity, String> {

    List<DeploymentEntity> findAllByVmIdOrderByCreatedAtDesc(String vmId);

    List<DeploymentEntity> findTop100ByOrderByCreatedAtDesc();

    long countByDeploymentTargetId(String deploymentTargetId);

    List<DeploymentEntity> findAllByStatus(DeploymentStatus status);

    Optional<DeploymentEntity> findTopByVmIdAndDeploymentTargetIdIsNullAndStatusOrderByCreatedAtDesc(
            String vmId, DeploymentStatus status);

    Optional<DeploymentEntity> findTopByDeploymentTargetIdOrderByCreatedAtDesc(String deploymentTargetId);

    @Query(value = """
            SELECT latest.*
              FROM (
                    SELECT DISTINCT ON (deployment.deployment_target_id) deployment.*
                      FROM deployments deployment
                      JOIN deployment_targets target
                        ON target.id = deployment.deployment_target_id
                       AND target.active = true
                     ORDER BY deployment.deployment_target_id, deployment.created_at DESC
              ) latest
              JOIN deployment_targets target ON target.id = latest.deployment_target_id
             WHERE (latest.status = 'SUCCEEDED'
                    AND target.latest_deployment_id IS DISTINCT FROM latest.id)
                OR (latest.status = 'ROLLED_BACK'
                    AND latest.previous_deployment_id IS NOT NULL
                    AND target.latest_deployment_id IS DISTINCT FROM latest.previous_deployment_id)
            """, nativeQuery = true)
    List<DeploymentEntity> findLatestDeploymentsNeedingActivePointerSync();

    List<DeploymentEntity> findAllByTriggerTypeAndStatusNotIn(
            DeploymentTriggerType triggerType, List<DeploymentStatus> terminalStatuses);

}
