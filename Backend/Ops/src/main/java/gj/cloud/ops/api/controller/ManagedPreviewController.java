package gj.cloud.ops.api.controller;

import gj.cloud.ops.application.preview.managed.ManagedPreviewService;
import gj.cloud.ops.application.preview.managed.dto.ManagedPreviewResponse;
import gj.cloud.ops.global.response.ApiResponse;
import gj.cloud.ops.global.security.OpsPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/ops/preview/deployments")
@RequiredArgsConstructor
public class ManagedPreviewController {
    private final ManagedPreviewService service;

    @GetMapping
    public ApiResponse<List<ManagedPreviewResponse>> list(@AuthenticationPrincipal OpsPrincipal principal) {
        return ApiResponse.ok(service.list(principal.userId()));
    }

    @GetMapping("/{id}")
    public ApiResponse<ManagedPreviewResponse> get(@PathVariable String id,
            @AuthenticationPrincipal OpsPrincipal principal) {
        return ApiResponse.ok(service.get(id, principal.userId()));
    }
}
