package gj.cloud.user.api.controller;

import gj.cloud.user.api.controller.spec.UsageApi;
import gj.cloud.user.application.usage.dto.UsageResponse;
import gj.cloud.user.application.usage.service.UsageService;
import gj.cloud.user.global.response.ApiResponse;
import gj.cloud.user.global.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UsageController implements UsageApi {

    private final UsageService usageService;

    @Override
    public ApiResponse<UsageResponse> getUsage(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(usageService.getUsage(principal.userId(), principal.email()));
    }
}
