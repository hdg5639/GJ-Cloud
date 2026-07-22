package gj.cloud.user.application.profile.dto;

import gj.cloud.user.domain.profile.entity.UserProfileEntity;

// 조직 초대용 사용자 검색 결과 — planType 등 민감하지 않은 최소 필드만 노출
public record ProfileSearchResult(
        String userId,
        String nickname,
        String email,
        String profileImageUrl
) {
    public static ProfileSearchResult from(UserProfileEntity entity) {
        return new ProfileSearchResult(
                entity.getUserId(),
                entity.getNickname(),
                entity.getEmail(),
                entity.getProfileImageUrl()
        );
    }
}
