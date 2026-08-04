package gj.cloud.user.domain.support.repository;

import gj.cloud.user.domain.support.entity.SupportInquiryEntity;
import gj.cloud.user.domain.support.enums.SupportInquiryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SupportInquiryRepository extends JpaRepository<SupportInquiryEntity, UUID> {
    Page<SupportInquiryEntity> findAllByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
    Page<SupportInquiryEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<SupportInquiryEntity> findAllByStatusOrderByCreatedAtDesc(SupportInquiryStatus status, Pageable pageable);
    Optional<SupportInquiryEntity> findByIdAndUserId(UUID id, String userId);
}
