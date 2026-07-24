package gj.cloud.user.application.upgrade.dto;

import gj.cloud.user.domain.upgrade.entity.UpgradeRequestEntity;
import gj.cloud.user.domain.upgrade.enums.UpgradeRequestStatus;
import gj.cloud.user.domain.upgrade.enums.UpgradeRequestType;
import gj.cloud.user.domain.plan.enums.PlanType;

import java.time.LocalDateTime;
import java.util.UUID;

public record UpgradeRequestResponse(
        UUID id,
        String userId,
        UpgradeRequestType type,
        PlanType targetPlanType,
        UpgradeRequestStatus status,
        String reason,
        LocalDateTime createdAt,
        LocalDateTime reviewedAt,
        String reviewedBy
) {
    public static UpgradeRequestResponse from(UpgradeRequestEntity entity) {
        return new UpgradeRequestResponse(
                entity.getId(),
                entity.getUserId(),
                entity.getType(),
                entity.getTargetPlanType(),
                entity.getStatus(),
                entity.getReason(),
                entity.getCreatedAt(),
                entity.getReviewedAt(),
                entity.getReviewedBy()
        );
    }
}
