package gj.cloud.user.api.controller.spec;

import gj.cloud.user.application.profile.dto.ProfileResponse;
import gj.cloud.user.application.profile.dto.ProfileUpdateRequest;
import gj.cloud.user.global.response.ApiResponse;
import gj.cloud.user.global.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Profile", description = "사용자 프로필 API")
@RequestMapping("/users/profile")
public interface ProfileApi {

    @Operation(summary = "내 프로필 조회", description = "프로필이 없으면 자동 생성합니다.")
    @GetMapping
    ApiResponse<ProfileResponse> getProfile(@AuthenticationPrincipal UserPrincipal principal);

    @Operation(summary = "프로필 수정", description = "닉네임, 프로필 이미지를 수정합니다.")
    @PatchMapping
    ApiResponse<ProfileResponse> updateProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ProfileUpdateRequest request
    );
}
