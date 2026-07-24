package gj.cloud.user.api.controller;

import gj.cloud.user.application.admin.dto.AdminUserResponse;
import gj.cloud.user.application.admin.dto.PlanUpdateRequest;
import gj.cloud.user.application.admin.service.AdminUserService;
import gj.cloud.user.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin - Users")
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public ApiResponse<List<AdminUserResponse>> listUsers() {
        return ApiResponse.ok(adminUserService.listUsers());
    }

    @GetMapping("/{userId}")
    public ApiResponse<AdminUserResponse> getUser(@PathVariable String userId) {
        return ApiResponse.ok(adminUserService.getUser(userId));
    }

    @PatchMapping("/{userId}/suspend")
    public ApiResponse<AdminUserResponse> suspendUser(@PathVariable String userId) {
        return ApiResponse.ok(adminUserService.suspendUser(userId));
    }

    @PatchMapping("/{userId}/activate")
    public ApiResponse<AdminUserResponse> activateUser(@PathVariable String userId) {
        return ApiResponse.ok(adminUserService.activateUser(userId));
    }

    @PatchMapping("/{userId}/plan")
    public ApiResponse<AdminUserResponse> updatePlan(
            @PathVariable String userId,
            @Valid @RequestBody PlanUpdateRequest request
    ) {
        return ApiResponse.ok(adminUserService.updatePlan(userId, request.planType()));
    }
}
