package gj.cloud.ops.domain.backup.repository;

import gj.cloud.ops.domain.backup.entity.DbBackupEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DbBackupRepository extends JpaRepository<DbBackupEntity, String> {

    List<DbBackupEntity> findAllByVmIdOrderByCreatedAtDesc(String vmId);
}
