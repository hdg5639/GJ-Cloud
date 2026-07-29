package gj.cloud.ops.domain.preview.repository;

import gj.cloud.ops.domain.preview.entity.RegressionSuiteRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RegressionSuiteRunRepository extends JpaRepository<RegressionSuiteRunEntity, String> {

    Optional<RegressionSuiteRunEntity> findByIdAndOwnerId(String id, String ownerId);

    List<RegressionSuiteRunEntity> findTop30BySuiteIdAndOwnerIdOrderByCreatedAtDesc(
            String suiteId, String ownerId);
}
