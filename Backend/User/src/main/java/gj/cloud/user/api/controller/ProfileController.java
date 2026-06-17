package gj.cloud.user.api.controller;

import gj.cloud.user.api.controller.spec.ProfileApi;
import gj.cloud.user.application.profile.dto.ProfileResponse;
import gj.cloud.user.application.profile.dto.ProfileUpdateRequest;
import gj.cloud.user.application.profile.service.ProfileService;
import gj.cloud.user.global.response.ApiResponse;
import gj.cloud.user.global.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ProfileController implements ProfileApi {

    private final ProfileService profileService;

    @Override
    public ApiResponse<ProfileResponse> getProfile(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(profileService.getProfile(principal.userId(), principal.email()));
    }

    @Override
    public ApiResponse<ProfileResponse> updateProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            ProfileUpdateRequest request
    ) {
        return ApiResponse.ok(profileService.updateProfile(principal.userId(), principal.email(), request));
    }
}
