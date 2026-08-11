package gj.cloud.user.api.controller;

import gj.cloud.user.application.docs.dto.*;
import gj.cloud.user.application.docs.service.DocsArticleService;
import gj.cloud.user.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Docs", description = "사용자 도움말 문서")
@RestController
@RequestMapping("/users/docs")
@RequiredArgsConstructor
public class DocsController {

    private final DocsArticleService docsArticleService;

    @GetMapping
    public ApiResponse<List<DocsArticleSummaryResponse>> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String category
    ) {
        return ApiResponse.ok(docsArticleService.listPublished(query, category));
    }

    @GetMapping("/page")
    public ApiResponse<Page<DocsArticleSummaryResponse>> listPage(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "18") int size
    ) {
        return ApiResponse.ok(docsArticleService.listPublishedPage(query, category, page, size));
    }

    @GetMapping("/featured")
    public ApiResponse<List<DocsArticleSummaryResponse>> featured() {
        return ApiResponse.ok(docsArticleService.listFeatured());
    }

    @GetMapping("/categories")
    public ApiResponse<List<DocsCategoryResponse>> categories() {
        return ApiResponse.ok(docsArticleService.listCategories());
    }

    @GetMapping("/{slug}")
    public ApiResponse<DocsArticleResponse> get(@PathVariable String slug) {
        return ApiResponse.ok(docsArticleService.getPublished(slug));
    }

    @GetMapping("/{slug}/page")
    public ApiResponse<DocsArticlePageResponse> getPage(@PathVariable String slug) {
        return ApiResponse.ok(docsArticleService.getPublishedPage(slug));
    }
}
