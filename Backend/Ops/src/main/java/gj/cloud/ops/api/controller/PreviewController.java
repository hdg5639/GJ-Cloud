package gj.cloud.ops.api.controller;

import gj.cloud.ops.application.preview.ai.AiPagePlanner;
import gj.cloud.ops.application.preview.ai.AiPageReviewer;
import gj.cloud.ops.application.preview.ai.PagePlanProposalResult;
import gj.cloud.ops.application.preview.ai.PageReviewFinding;
import gj.cloud.ops.application.preview.analysis.GenerationMode;
import gj.cloud.ops.application.preview.analysis.PageDraft;
import gj.cloud.ops.application.preview.binding.ApiBinding;
import gj.cloud.ops.application.preview.dto.PreviewAnalysisResult;
import gj.cloud.ops.application.preview.dto.PreviewAnalyzeRequest;
import gj.cloud.ops.application.preview.dto.PreviewBlocksRequest;
import gj.cloud.ops.application.preview.dto.PreviewBlocksResponse;
import gj.cloud.ops.application.preview.dto.PreviewPlanApplyRequest;
import gj.cloud.ops.application.preview.dto.PreviewPlanApplyResponse;
import gj.cloud.ops.application.preview.dto.PreviewPlanRequest;
import gj.cloud.ops.application.preview.dto.PreviewReviewRequest;
import gj.cloud.ops.application.preview.flow.FlowBlueprint;
import gj.cloud.ops.application.preview.flow.RuleBasedFlowGenerator;
import gj.cloud.ops.application.preview.planning.model.PagePlan;
import gj.cloud.ops.application.preview.planning.model.PagePlanMapper;
import gj.cloud.ops.application.preview.planning.patch.PagePlanPatchApplyResult;
import gj.cloud.ops.application.preview.planning.patch.PlanPatchState;
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

@Tag(name = "Preview", description = "Auto Preview — OpenAPI 기반 테스트 프론트 자동 생성")
@RestController
@RequestMapping("/ops/preview")
@RequiredArgsConstructor
public class PreviewController {

    private final PreviewAnalysisService previewAnalysisService;
    private final PreviewBlueprintService previewBlueprintService;
    private final AiPageReviewer aiPageReviewer;
    private final AiPagePlanner aiPagePlanner;
    private final RuleBasedFlowGenerator ruleBasedFlowGenerator;

    @Operation(summary = "OpenAPI 문서 분석", description = "OpenAPI 3.x 문서를 결정론적으로 분석해 capability와 페이지 초안을 반환합니다. 배포는 수행하지 않습니다.")
    @PostMapping("/analyze")
    public ApiResponse<PreviewAnalysisResult> analyze(@Valid @RequestBody PreviewAnalyzeRequest request) {
        return ApiResponse.ok(previewAnalysisService.analyze(request));
    }

    @Operation(summary = "페이지 Blueprint Block 계산", description = "현재 PagePlan을 실제 렌더링 Block으로 컴파일합니다. pagePlans가 없는 구버전 요청은 pages를 사용합니다.")
    @PostMapping("/blocks")
    public ApiResponse<PreviewBlocksResponse> blocks(@Valid @RequestBody PreviewBlocksRequest request) {
        var blocks = request.pagePlans() != null && !request.pagePlans().isEmpty()
                ? previewBlueprintService.compilePagePlanBlocks(request.pagePlans(), request.capabilities(), request.purpose())
                : previewBlueprintService.compilePageBlocks(request.pages(), request.capabilities(), request.purpose());
        // 파츠 선택을 얹는다(사용자 오버라이드 우선, 없으면 자동선택). 검증(PreviewDeployController)은
        // 기본 Block으로 하므로 여기서 파츠 치환해도 배포가 막히지 않는다.
        blocks = previewBlueprintService.selectBlueprintParts(
                blocks, request.capabilities(), request.purpose(), request.partOverrides());
        return ApiResponse.ok(new PreviewBlocksResponse(blocks));
    }

    @Operation(summary = "페이지 초안 AI 검수", description = "현재 capability/페이지 초안을 AI가 advisory로 검수하며 실제 계획은 수정하지 않습니다.")
    @PostMapping("/review")
    public ApiResponse<List<PageReviewFinding>> review(
            @AuthenticationPrincipal OpsPrincipal principal,
            @Valid @RequestBody PreviewReviewRequest request
    ) {
        return ApiResponse.ok(aiPageReviewer.review(
                principal.userId(), request.serviceDescription(), request.capabilities(), request.pages()));
    }

    @Operation(summary = "AI 기반 페이지·워크플로우 재구성 제안", description = "현재 PagePlan/Flow/Binding 정본을 기준으로 구조화된 Patch를 제안하고 각 operation의 구조 검증 결과를 반환합니다.")
    @PostMapping("/plan/propose")
    public ApiResponse<PagePlanProposalResult> proposePlan(
            @AuthenticationPrincipal OpsPrincipal principal,
            @Valid @RequestBody PreviewPlanRequest request
    ) {
        PlanPatchState state = currentState(request.pages(), request.pagePlans(), request.flows(), request.bindings(),
                request.capabilities());
        return ApiResponse.ok(aiPagePlanner.propose(principal.userId(), request.serviceDescription(),
                request.purpose(), request.capabilities(), state));
    }

    @Operation(summary = "페이지·워크플로우 Patch 적용", description = "사용자가 선택한 operation을 PagePlan/Flow/Binding 정본에 all-or-nothing으로 적용하고 최종 배포 가능성까지 검증합니다.")
    @PostMapping("/plan/apply")
    public ApiResponse<PreviewPlanApplyResponse> applyPlan(@Valid @RequestBody PreviewPlanApplyRequest request) {
        PlanPatchState original = currentState(request.pages(), request.pagePlans(), request.flows(), request.bindings(),
                request.capabilities());
        PagePlanPatchApplyResult result = aiPagePlanner.applySelected(original, request.capabilities(), request.operations());
        boolean aiChangesApplied = result.succeeded()
                && request.operations() != null && !request.operations().isEmpty()
                && result.decisions() != null && !result.decisions().isEmpty();
        GenerationMode generationMode = aiChangesApplied
                ? GenerationMode.SERVICE_AWARE : GenerationMode.RULE_BASED;
        PlanPatchState effective = result.state();
        List<PageDraft> pages = PagePlanMapper.toDrafts(effective.pagePlans());
        return ApiResponse.ok(new PreviewPlanApplyResponse(
                pages,
                effective.pagePlans(),
                effective.flows(),
                effective.bindings(),
                result.decisions(),
                result.errors(),
                generationMode
        ));
    }

    private PlanPatchState currentState(
            List<PageDraft> pages,
            List<PagePlan> pagePlans,
            List<FlowBlueprint> flows,
            List<ApiBinding> bindings,
            List<gj.cloud.ops.application.preview.analysis.Capability> capabilities
    ) {
        List<PagePlan> effectivePlans = pagePlans == null || pagePlans.isEmpty()
                ? PagePlanMapper.from(pages, capabilities)
                : pagePlans;
        if (flows == null || bindings == null) {
            RuleBasedFlowGenerator.ValidatedResult generated =
                    ruleBasedFlowGenerator.generateValidated(effectivePlans, capabilities);
            return new PlanPatchState(effectivePlans, generated.result().flows(), generated.result().bindings());
        }
        return new PlanPatchState(effectivePlans, flows, bindings);
    }
}
