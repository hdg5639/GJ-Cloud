package gj.cloud.ops.domain.deployment.repository;

import gj.cloud.ops.domain.deployment.entity.DeploymentEntity;
import gj.cloud.ops.domain.deployment.enums.DeploymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeploymentRepository extends JpaRepository<DeploymentEntity, String> {

    List<DeploymentEntity> findAllByVmIdOrderByCreatedAtDesc(String vmId);

    Optional<DeploymentEntity> findTopByVmIdAndStatusOrderByCreatedAtDesc(String vmId, DeploymentStatus status);

    // D.9 동시성 제어 보조: DB 상태 기준으로도 활성 배포 여부를 이중 확인할 때 사용 (1차 방어는 Redis 락)
    boolean existsByVmIdAndStatusNotIn(String vmId, List<DeploymentStatus> terminalStatuses);
}
