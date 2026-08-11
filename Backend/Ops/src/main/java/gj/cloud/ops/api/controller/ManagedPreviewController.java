package gj.cloud.ops.api.controller;

import gj.cloud.ops.application.preview.managed.ManagedPreviewService;
import gj.cloud.ops.application.preview.managed.dto.ManagedPreviewResponse;
import gj.cloud.ops.global.response.ApiResponse;
import gj.cloud.ops.global.security.OpsPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Managed Preview", description = "현재 사용자의 관리형 Auto Preview 배포 조회")
@RestController
@RequestMapping("/ops/preview/deployments")
@RequiredArgsConstructor
public class ManagedPreviewController {
    private final ManagedPreviewService service;

    @Operation(
            summary = "관리형 Preview 목록 조회",
            description = "현재 사용자가 공용 Worker에 배포한 Preview를 조회하고 만료·Runtime 상태를 반영합니다."
    )
    @GetMapping
    public ApiResponse<List<ManagedPreviewResponse>> list(@AuthenticationPrincipal OpsPrincipal principal) {
        return ApiResponse.ok(service.list(principal.userId()));
    }

    @Operation(
            summary = "관리형 Preview 상세 조회",
            description = "현재 사용자가 소유한 관리형 Preview의 URL, 상태, 배포 ID와 만료 시각을 조회합니다."
    )
    @GetMapping("/{id}")
    public ApiResponse<ManagedPreviewResponse> get(@PathVariable String id,
            @AuthenticationPrincipal OpsPrincipal principal) {
        return ApiResponse.ok(service.get(id, principal.userId()));
    }
}
