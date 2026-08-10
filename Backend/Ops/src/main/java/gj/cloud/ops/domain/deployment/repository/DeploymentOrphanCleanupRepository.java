package gj.cloud.ops.domain.deployment.repository;

import gj.cloud.ops.domain.deployment.entity.DeploymentOrphanCleanupEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeploymentOrphanCleanupRepository extends JpaRepository<DeploymentOrphanCleanupEntity, String> {
    List<DeploymentOrphanCleanupEntity> findTop100ByOrderByCreatedAtDesc();
}
