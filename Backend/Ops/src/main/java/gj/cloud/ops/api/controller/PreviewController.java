package gj.cloud.ops.api.controller;

import gj.cloud.ops.application.preview.ai.AiPagePlanner;
import gj.cloud.ops.application.preview.ai.AiPageReviewer;
import gj.cloud.ops.application.preview.ai.PagePlanResult;
import gj.cloud.ops.application.preview.ai.PageReviewFinding;
import gj.cloud.ops.application.preview.analysis.GenerationMode;
import gj.cloud.ops.application.preview.dto.PreviewAnalysisResult;
import gj.cloud.ops.application.preview.dto.PreviewAnalyzeRequest;
import gj.cloud.ops.application.preview.dto.PreviewBlocksRequest;
import gj.cloud.ops.application.preview.dto.PreviewBlocksResponse;
import gj.cloud.ops.application.preview.dto.PreviewPlanRequest;
import gj.cloud.ops.application.preview.dto.PreviewPlanResponse;
import gj.cloud.ops.application.preview.dto.PreviewReviewRequest;
import gj.cloud.ops.application.preview.service.PreviewAnalysisService;
import gj.cloud.ops.application.preview.service.PreviewBlueprintService;
import gj.cloud.ops.global.response.ApiResponse;
import gj.cloud.ops.global.security.OpsPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Auto Preview (GamjaBox_2.0_Key_Features.md 1단계) Phase A — OpenAPI 문서를 결정론적으로 분석해
// capability/페이지 초안만 반환한다. 특정 VM에 종속된 동작이 아니라 로그인한 사용자면 누구나 호출
// 가능하고(SecurityConfig의 anyRequest().authenticated()로 이미 보호됨), 이 단계는 배포를 전혀 수행하지 않는다.
@Tag(name = "Preview", description = "Auto Preview — OpenAPI 기반 테스트 프론트 자동 생성")
@RestController
@RequestMapping("/ops/preview")
@RequiredArgsConstructor
public class PreviewController {

    private final PreviewAnalysisService previewAnalysisService;
    private final PreviewBlueprintService previewBlueprintService;
    private final AiPageReviewer aiPageReviewer;
    private final AiPagePlanner aiPagePlanner;

    @Operation(summary = "OpenAPI 문서 분석", description = "OpenAPI 3.x 문서를 결정론적으로 분석해 capability와 페이지 초안을 반환합니다. 배포는 수행하지 않습니다.")
    @PostMapping("/analyze")
    public ApiResponse<PreviewAnalysisResult> analyze(@Valid @RequestBody PreviewAnalyzeRequest request) {
        return ApiResponse.ok(previewAnalysisService.analyze(request));
    }

    @Operation(summary = "페이지 Blueprint Block 계산", description = "capability/페이지 초안(+목적)으로 실제 렌더링에 쓰일 Block 목록을 계산합니다. " +
            "analyze/plan 응답을 받은 직후뿐 아니라, accessTokenPath 지정이나 로그인 API 수동 등록처럼 프론트가 capability/페이지를 " +
            "서버 재호출 없이 로컬로 편집했을 때도 다시 호출해야 합니다 — 라이브 프리뷰는 이 결과를 그대로 소비할 뿐 조립 규칙을 직접 판단하지 않습니다.")
    @PostMapping("/blocks")
    public ApiResponse<PreviewBlocksResponse> blocks(@Valid @RequestBody PreviewBlocksRequest request) {
        return ApiResponse.ok(new PreviewBlocksResponse(
                previewBlueprintService.compilePageBlocks(request.pages(), request.capabilities(), request.purpose())));
    }

    @Operation(summary = "페이지 초안 AI 검수", description = "analyze가 반환한 capability/페이지 초안을 AI가 advisory로 1회 검수합니다. 페이지를 직접 수정하지 않고 코멘트만 반환하며, 실패해도 빈 목록을 반환할 뿐 분석 결과 자체는 그대로 유효합니다.")
    @PostMapping("/review")
    public ApiResponse<List<PageReviewFinding>> review(
            @AuthenticationPrincipal OpsPrincipal principal,
            @Valid @RequestBody PreviewReviewRequest request
    ) {
        return ApiResponse.ok(aiPageReviewer.review(
                principal.userId(), request.serviceDescription(), request.capabilities(), request.pages()));
    }

    @Operation(summary = "AI 기반 페이지 재구성 제안", description = "capability/페이지 초안을 서비스 설명에 맞춰 AI가 재구성 제안하고, " +
            "검증을 통과한 제안만 실제로 적용해 돌려줍니다. 검증에 실패하면 입력받은 페이지를 그대로 반환합니다(배포를 막지 않음).")
    @PostMapping("/plan")
    public ApiResponse<PreviewPlanResponse> plan(
            @AuthenticationPrincipal OpsPrincipal principal,
            @Valid @RequestBody PreviewPlanRequest request
    ) {
        PagePlanResult result = aiPagePlanner.plan(principal.userId(), request.serviceDescription(),
                request.purpose(), request.capabilities(), request.pages());
        GenerationMode generationMode = result.aiSucceeded() ? GenerationMode.SERVICE_AWARE : GenerationMode.RULE_BASED;
        return ApiResponse.ok(new PreviewPlanResponse(result.pages(), result.decisions(), generationMode));
    }
}
