package gj.cloud.ops.domain.preview.repository;

import gj.cloud.ops.domain.preview.entity.CustomScenarioEntity;
import gj.cloud.ops.domain.preview.enums.CustomScenarioStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomScenarioRepository extends JpaRepository<CustomScenarioEntity, String> {

    Optional<CustomScenarioEntity> findByIdAndOwnerId(String id, String ownerId);

    List<CustomScenarioEntity> findAllByServiceIdAndOwnerIdAndStatusNotOrderByUpdatedAtDesc(
            String serviceId,
            String ownerId,
            CustomScenarioStatus status
    );
}
