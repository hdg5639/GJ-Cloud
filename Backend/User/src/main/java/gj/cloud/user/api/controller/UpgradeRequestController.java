package gj.cloud.user.api.controller;

import gj.cloud.user.application.upgrade.dto.CreateUpgradeRequestRequest;
import gj.cloud.user.application.upgrade.dto.PagedUpgradeRequestResponse;
import gj.cloud.user.application.upgrade.dto.ReviewUpgradeRequestRequest;
import gj.cloud.user.application.upgrade.dto.UpgradeRequestResponse;
import gj.cloud.user.application.upgrade.service.UpgradeRequestService;
import gj.cloud.user.domain.upgrade.enums.UpgradeRequestStatus;
import gj.cloud.user.global.response.ApiResponse;
import gj.cloud.user.global.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "UpgradeRequest")
@RestController
@RequiredArgsConstructor
public class UpgradeRequestController {

    private final UpgradeRequestService upgradeRequestService;

    @Operation(summary = "플랜 변경 요청 생성")
    @PostMapping("/users/{userId}/upgrade-requests")
    public ApiResponse<UpgradeRequestResponse> createUpgradeRequest(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String userId,
            @RequestBody CreateUpgradeRequestRequest request
    ) {
        if (!userId.equals(principal.userId())) {
            throw new RuntimeException("Forbidden");
        }
        UpgradeRequestResponse response = upgradeRequestService.createRequest(userId, request);
        return ApiResponse.ok(response);
    }

    @Operation(summary = "사용자 요청 목록 조회")
    @GetMapping("/users/{userId}/upgrade-requests")
    public ApiResponse<List<UpgradeRequestResponse>> getUserUpgradeRequests(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String userId
    ) {
        if (!userId.equals(principal.userId())) {
            throw new RuntimeException("Forbidden");
        }
        List<UpgradeRequestResponse> responses = upgradeRequestService.getUserRequests(userId);
        return ApiResponse.ok(responses);
    }

    @Operation(summary = "플랜 변경 요청 취소")
    @DeleteMapping("/users/{userId}/upgrade-requests/{requestId}")
    public ApiResponse<Void> cancelUpgradeRequest(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String userId,
            @PathVariable UUID requestId
    ) {
        if (!userId.equals(principal.userId())) {
            throw new RuntimeException("Forbidden");
        }
        upgradeRequestService.cancelRequest(requestId, userId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "대기 중인 플랜 변경 요청 목록 (관리자)")
    @GetMapping("/admin/upgrade-requests")
    public ApiResponse<PagedUpgradeRequestResponse> getPendingUpgradeRequests(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<UpgradeRequestResponse> responses = upgradeRequestService.getRequestsByStatus(UpgradeRequestStatus.PENDING, page, size);
        return ApiResponse.ok(PagedUpgradeRequestResponse.from(responses));
    }

    @Operation(summary = "플랜 변경 요청 검토 (관리자)")
    @PatchMapping("/admin/upgrade-requests/{requestId}")
    public ApiResponse<UpgradeRequestResponse> reviewUpgradeRequest(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID requestId,
            @RequestBody ReviewUpgradeRequestRequest request
    ) {
        UpgradeRequestResponse response = upgradeRequestService.reviewRequest(principal.userId(), requestId, request);
        return ApiResponse.ok(response);
    }
}
