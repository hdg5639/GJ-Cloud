package gj.cloud.user.domain.upgrade.repository;

import gj.cloud.user.domain.upgrade.entity.UpgradeRequestEntity;
import gj.cloud.user.domain.upgrade.enums.UpgradeRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UpgradeRequestRepository extends JpaRepository<UpgradeRequestEntity, UUID> {
    List<UpgradeRequestEntity> findAllByStatus(UpgradeRequestStatus status);
    List<UpgradeRequestEntity> findAllByUserId(String userId);
    Optional<UpgradeRequestEntity> findByUserIdAndStatus(String userId, UpgradeRequestStatus status);
    Page<UpgradeRequestEntity> findByStatusOrderByCreatedAtDesc(UpgradeRequestStatus status, Pageable pageable);
}
