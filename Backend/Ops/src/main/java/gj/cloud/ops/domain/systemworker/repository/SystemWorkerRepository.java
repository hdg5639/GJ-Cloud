package gj.cloud.ops.domain.systemworker.repository;

import gj.cloud.ops.domain.systemworker.entity.SystemWorkerEntity;
import gj.cloud.ops.domain.systemworker.enums.SystemWorkerRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SystemWorkerRepository extends JpaRepository<SystemWorkerEntity, String> {
    Optional<SystemWorkerEntity> findByRole(SystemWorkerRole role);
}
