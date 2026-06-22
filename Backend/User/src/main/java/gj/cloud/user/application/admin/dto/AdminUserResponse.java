package gj.cloud.user.application.admin.dto;

import gj.cloud.user.domain.profile.entity.UserProfileEntity;

import java.time.LocalDateTime;

public record AdminUserResponse(
        String userId,
        String email,
        String nickname,
        String planType,
        boolean suspended,
        LocalDateTime createdAt
) {
    public static AdminUserResponse from(UserProfileEntity entity) {
        return new AdminUserResponse(
                entity.getUserId(),
                entity.getEmail(),
                entity.getNickname(),
                entity.getPlanType().name(),
                entity.isSuspended(),
                entity.getCreatedAt()
        );
    }
}
