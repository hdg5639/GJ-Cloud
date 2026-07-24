package gj.cloud.user.application.profile.dto;

import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(
        @Size(min = 2, max = 12) String nickname,
        @Size(max = 500) String profileImageUrl
) {}
