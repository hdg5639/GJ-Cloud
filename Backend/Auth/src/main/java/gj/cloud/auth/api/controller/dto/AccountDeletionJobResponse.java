package gj.cloud.auth.api.controller.dto;

import gj.cloud.auth.domain.deletion.entity.AccountDeletionJobEntity;
import gj.cloud.auth.domain.deletion.enums.AccountDeletionJobStatus;

import java.time.LocalDateTime;

public record AccountDeletionJobResponse(
        String id,
        String userId,
        String email,
        AccountDeletionJobStatus status,
        boolean userServiceDone,
        boolean vmServiceDone,
        int attemptCount,
        String lastError,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AccountDeletionJobResponse from(AccountDeletionJobEntity entity) {
        return new AccountDeletionJobResponse(
                entity.getId(),
                entity.getUserId(),
                entity.getEmail(),
                entity.getStatus(),
                entity.isUserServiceDone(),
                entity.isVmServiceDone(),
                entity.getAttemptCount(),
                entity.getLastError(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
