package gj.cloud.user.api.controller;

import gj.cloud.user.application.docs.dto.DocsArticleResponse;
import gj.cloud.user.application.docs.dto.DocsAdminStatsResponse;
import gj.cloud.user.application.docs.dto.DocsArticleSummaryResponse;
import gj.cloud.user.application.docs.dto.DocsArticleUpsertRequest;
import gj.cloud.user.application.docs.dto.DocsImageUploadResponse;
import gj.cloud.user.application.docs.service.DocsArticleService;
import gj.cloud.user.domain.docs.enums.DocsArticleStatus;
import gj.cloud.user.global.response.ApiResponse;
import gj.cloud.user.global.security.UserPrincipal;
import gj.cloud.user.global.storage.DocsImageStorage;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Tag(name = "Admin - Docs")
@RestController
@RequestMapping("/admin/docs")
@RequiredArgsConstructor
public class AdminDocsController {

    private final DocsArticleService docsArticleService;
    private final DocsImageStorage docsImageStorage;

    @GetMapping
    public ApiResponse<List<DocsArticleSummaryResponse>> list() {
        return ApiResponse.ok(docsArticleService.listAdmin());
    }

    @GetMapping("/page")
    public ApiResponse<Page<DocsArticleSummaryResponse>> listPage(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) DocsArticleStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(docsArticleService.listAdminPage(query, status, page, size));
    }

    @GetMapping("/stats")
    public ApiResponse<DocsAdminStatsResponse> stats() {
        return ApiResponse.ok(docsArticleService.getAdminStats());
    }

    @GetMapping("/{id}")
    public ApiResponse<DocsArticleResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(docsArticleService.getAdmin(id));
    }

    @PostMapping
    public ApiResponse<DocsArticleResponse> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody DocsArticleUpsertRequest request
    ) {
        return ApiResponse.ok(docsArticleService.create(principal.userId(), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<DocsArticleResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody DocsArticleUpsertRequest request
    ) {
        return ApiResponse.ok(docsArticleService.update(id, request));
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<DocsArticleResponse> publish(@PathVariable UUID id) {
        return ApiResponse.ok(docsArticleService.publish(id));
    }

    @PostMapping("/{id}/unpublish")
    public ApiResponse<DocsArticleResponse> unpublish(@PathVariable UUID id) {
        return ApiResponse.ok(docsArticleService.unpublish(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        docsArticleService.delete(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/images")
    public ApiResponse<DocsImageUploadResponse> uploadImage(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(docsImageStorage.store(file));
    }
}
