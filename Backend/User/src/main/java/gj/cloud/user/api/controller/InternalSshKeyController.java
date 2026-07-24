package gj.cloud.user.api.controller;

import gj.cloud.user.application.profile.service.ProfileService;
import gj.cloud.user.application.sshkey.dto.SshKeyInternalResponse;
import gj.cloud.user.application.sshkey.service.SshKeyService;
import gj.cloud.user.global.response.ApiResponse;
import gj.cloud.user.global.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalSshKeyController {

    private final SshKeyService sshKeyService;
    private final ProfileService profileService;

    @GetMapping("/ssh-keys/{keyId}")
    public ApiResponse<SshKeyInternalResponse> getKey(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String keyId
    ) {
        return ApiResponse.ok(sshKeyService.getKeyById(principal.userId(), keyId));
    }

    @GetMapping("/users/plan")
    public ApiResponse<String> getUserPlan(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(profileService.getPlanType(principal.userId()));
    }
}
