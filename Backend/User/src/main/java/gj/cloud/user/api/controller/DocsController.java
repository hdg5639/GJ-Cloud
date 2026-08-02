package gj.cloud.user.api.controller;

import gj.cloud.user.application.docs.dto.DocsArticleResponse;
import gj.cloud.user.application.docs.dto.DocsArticleSummaryResponse;
import gj.cloud.user.application.docs.dto.DocsCategoryResponse;
import gj.cloud.user.application.docs.service.DocsArticleService;
import gj.cloud.user.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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

    @GetMapping("/categories")
    public ApiResponse<List<DocsCategoryResponse>> categories() {
        return ApiResponse.ok(docsArticleService.listCategories());
    }

    @GetMapping("/{slug}")
    public ApiResponse<DocsArticleResponse> get(@PathVariable String slug) {
        return ApiResponse.ok(docsArticleService.getPublished(slug));
    }
}
