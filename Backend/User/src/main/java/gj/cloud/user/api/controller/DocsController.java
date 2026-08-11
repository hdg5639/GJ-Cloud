package gj.cloud.user.api.controller;

import gj.cloud.user.application.docs.dto.*;
import gj.cloud.user.application.docs.service.DocsArticleService;
import gj.cloud.user.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
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

    @Operation(
            summary = "발행 문서 목록 조회",
            description = "검색어와 카테고리로 발행 문서를 조회하는 호환 API입니다. 본문을 제외한 요약을 최대 48건 반환합니다."
    )
    @GetMapping
    public ApiResponse<List<DocsArticleSummaryResponse>> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String category
    ) {
        return ApiResponse.ok(docsArticleService.listPublished(query, category));
    }

    @Operation(
            summary = "발행 문서 페이지 조회",
            description = "검색어와 카테고리로 발행 문서를 DB 페이징 조회합니다. page는 1부터 시작하고 size는 최대 48입니다."
    )
    @GetMapping("/page")
    public ApiResponse<Page<DocsArticleSummaryResponse>> listPage(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "18") int size
    ) {
        return ApiResponse.ok(docsArticleService.listPublishedPage(query, category, page, size));
    }

    @Operation(summary = "추천 문서 조회", description = "추천으로 발행된 문서를 정렬 순서에 따라 최대 2건 반환합니다.")
    @GetMapping("/featured")
    public ApiResponse<List<DocsArticleSummaryResponse>> featured() {
        return ApiResponse.ok(docsArticleService.listFeatured());
    }

    @Operation(summary = "문서 카테고리 조회", description = "발행 문서가 속한 카테고리와 카테고리별 문서 수를 반환합니다.")
    @GetMapping("/categories")
    public ApiResponse<List<DocsCategoryResponse>> categories() {
        return ApiResponse.ok(docsArticleService.listCategories());
    }

    @Operation(summary = "발행 문서 상세 조회", description = "slug로 발행 문서 본문을 조회하고 조회수를 기록합니다.")
    @GetMapping("/{slug}")
    public ApiResponse<DocsArticleResponse> get(@PathVariable String slug) {
        return ApiResponse.ok(docsArticleService.getPublished(slug));
    }

    @Operation(
            summary = "발행 문서 페이지 데이터 조회",
            description = "문서 본문과 같은 카테고리의 탐색 목록을 함께 반환하고 조회수를 기록합니다. 탐색 목록은 최대 100건입니다."
    )
    @GetMapping("/{slug}/page")
    public ApiResponse<DocsArticlePageResponse> getPage(@PathVariable String slug) {
        return ApiResponse.ok(docsArticleService.getPublishedPage(slug));
    }
}
