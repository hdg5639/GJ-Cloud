package gj.cloud.ops.domain.preview.repository;

import gj.cloud.ops.domain.preview.entity.RegressionSuiteEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RegressionSuiteRepository extends JpaRepository<RegressionSuiteEntity, String> {

    Optional<RegressionSuiteEntity> findByIdAndOwnerIdAndActiveTrue(String id, String ownerId);

    List<RegressionSuiteEntity> findAllByServiceIdAndOwnerIdAndActiveTrueOrderByUpdatedAtDesc(
            String serviceId, String ownerId);

    List<RegressionSuiteEntity> findAllByDeploymentTargetIdAndRunOnDeploymentTrueAndActiveTrue(
            String deploymentTargetId);
}
