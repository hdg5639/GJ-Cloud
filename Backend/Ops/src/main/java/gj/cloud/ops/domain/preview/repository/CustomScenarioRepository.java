package gj.cloud.ops.domain.preview.repository;

import gj.cloud.ops.domain.preview.entity.CustomScenarioEntity;
import gj.cloud.ops.domain.preview.enums.CustomScenarioStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface CustomScenarioRepository extends JpaRepository<CustomScenarioEntity, String> {

    Optional<CustomScenarioEntity> findByIdAndOwnerId(String id, String ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select scenario from CustomScenarioEntity scenario"
            + " where scenario.id = :id and scenario.ownerId = :ownerId")
    Optional<CustomScenarioEntity> findLockedByIdAndOwnerId(
            @Param("id") String id,
            @Param("ownerId") String ownerId
    );

    List<CustomScenarioEntity> findAllByServiceIdAndOwnerIdAndStatusNotOrderByUpdatedAtDesc(
            String serviceId,
            String ownerId,
            CustomScenarioStatus status
    );
}
