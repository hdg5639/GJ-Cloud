package gj.cloud.ops.domain.deployment.repository;

import gj.cloud.ops.domain.deployment.entity.DeploymentEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DeploymentEventRepository extends JpaRepository<DeploymentEventEntity, String> {

    List<DeploymentEventEntity> findAllByDeploymentIdAndSequenceGreaterThanOrderBySequenceAsc(
            String deploymentId, long afterSequence);

    List<DeploymentEventEntity> findTop1000ByDeploymentIdOrderBySequenceDesc(String deploymentId);

    @Query(value = """
            SELECT DISTINCT ON (deployment_id) event.*
              FROM deployment_events event
             WHERE event.deployment_id IN (:deploymentIds)
             ORDER BY event.deployment_id ASC, event.sequence DESC
            """, nativeQuery = true)
    List<DeploymentEventEntity> findLatestByDeploymentIdIn(
            @Param("deploymentIds") List<String> deploymentIds);

    @Query("SELECT MAX(e.sequence) FROM DeploymentEventEntity e WHERE e.deploymentId = :deploymentId")
    Optional<Long> findMaxSequence(@Param("deploymentId") String deploymentId);
}
