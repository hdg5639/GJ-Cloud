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

    @Query("SELECT MAX(e.sequence) FROM DeploymentEventEntity e WHERE e.deploymentId = :deploymentId")
    Optional<Long> findMaxSequence(@Param("deploymentId") String deploymentId);
}
