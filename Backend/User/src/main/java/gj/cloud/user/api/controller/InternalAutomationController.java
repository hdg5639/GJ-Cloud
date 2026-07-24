package gj.cloud.user.api.controller;

import gj.cloud.user.application.profile.service.ProfileService;
import gj.cloud.user.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@RequestMapping("/internal/automation")
@RequiredArgsConstructor
public class InternalAutomationController {

    private final ProfileService profileService;

    @GetMapping("/users/{userId}/plan")
    public ApiResponse<String> getPlan(@PathVariable String userId) {
        return ApiResponse.ok(profileService.getPlanType(userId));
    }
}
