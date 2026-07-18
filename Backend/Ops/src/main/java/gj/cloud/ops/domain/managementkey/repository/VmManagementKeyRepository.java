package gj.cloud.ops.domain.managementkey.repository;

import gj.cloud.ops.domain.managementkey.entity.VmManagementKeyEntity;
import gj.cloud.ops.domain.managementkey.enums.KeyStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VmManagementKeyRepository extends JpaRepository<VmManagementKeyEntity, String> {
    Optional<VmManagementKeyEntity> findByVmId(String vmId);

    List<VmManagementKeyEntity> findAllByStatus(KeyStatus status);
}
