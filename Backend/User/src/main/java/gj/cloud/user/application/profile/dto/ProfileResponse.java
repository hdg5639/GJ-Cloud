package gj.cloud.user.application.profile.dto;

import gj.cloud.user.domain.profile.entity.UserProfileEntity;

public record ProfileResponse(
        String userId,
        String email,
        String nickname,
        String profileImageUrl,
        String planType
) {
    public static ProfileResponse from(UserProfileEntity entity) {
        return new ProfileResponse(
                entity.getUserId(),
                entity.getEmail(),
                entity.getNickname(),
                entity.getProfileImageUrl(),
                entity.getPlanType().name()
        );
    }
}
