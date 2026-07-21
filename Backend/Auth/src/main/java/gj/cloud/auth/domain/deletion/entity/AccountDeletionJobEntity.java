package gj.cloud.auth.domain.deletion.entity;

import gj.cloud.auth.domain.deletion.enums.AccountDeletionJobStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

// REL-001: 회원 탈퇴 시 User/VM 서비스 데이터 정리를 재시도 가능하게 추적하는 아웃박스 레코드.
@Entity
@Table(name = "account_deletion_jobs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class AccountDeletionJobEntity {

    private static final int MAX_ATTEMPTS = 10;

    @Id
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountDeletionJobStatus status;

    @Column(name = "user_service_done", nullable = false)
    private boolean userServiceDone;

    @Column(name = "vm_service_done", nullable = false)
    private boolean vmServiceDone;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public static AccountDeletionJobEntity create(String userId, String email) {
        LocalDateTime now = LocalDateTime.now();
        return AccountDeletionJobEntity.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .email(email)
                .status(AccountDeletionJobStatus.PENDING)
                .userServiceDone(false)
                .vmServiceDone(false)
                .attemptCount(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public void markUserServiceDone() {
        this.userServiceDone = true;
        completeIfDone();
    }

    public void markVmServiceDone() {
        this.vmServiceDone = true;
        completeIfDone();
    }

    private void completeIfDone() {
        if (this.userServiceDone && this.vmServiceDone) {
            this.status = AccountDeletionJobStatus.COMPLETED;
            this.lastError = null;
        }
    }

    public void recordFailure(String error) {
        this.attemptCount++;
        this.lastError = error;
        this.status = this.attemptCount >= MAX_ATTEMPTS
                ? AccountDeletionJobStatus.FAILED_MANUAL_REVIEW
                : AccountDeletionJobStatus.FAILED_RETRYABLE;
    }
}
