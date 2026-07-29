package gj.cloud.ops.application.preview.dto;

import gj.cloud.ops.application.preview.analysis.AuthStrategy;
import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.GenerationMode;
import gj.cloud.ops.application.preview.analysis.PageDraft;
import gj.cloud.ops.application.preview.binding.ApiBinding;
import gj.cloud.ops.application.preview.flow.FlowBlueprint;
import gj.cloud.ops.application.preview.planning.model.PagePlan;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.CompiledScenario;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.PreviewMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

// 분석 이후 사용자가 확정한 최신 Product Blueprint 상태를 그대로 배포한다. 서버는 이 상태를 다시
// 최종 검증하며, pagePlans가 없는 구버전 요청만 pages에서 규칙 기반으로 재생성한다.
public record PreviewDeployRequest(
        @NotBlank @Size(max = 60) String targetName,
        @NotBlank String apiBaseUrl,
        @NotEmpty List<Capability> capabilities,
        @NotEmpty List<PageDraft> pages,
        List<PagePlan> pagePlans,
        List<FlowBlueprint> flows,
        List<ApiBinding> bindings,
        @NotNull AuthStrategy authStrategy,
        PreviewAnalyzeRequest.Purpose purpose,
        GenerationMode generationMode,
        List<CompiledScenario> scenarios,
        PreviewMode previewMode,
        // Phase C 사용자 파츠 오버라이드 — "pageId/blockInstanceId" → 강제 componentId. 배포 산출물의
        // 파츠 선택에 반영된다(라이브 프리뷰에서 고른 그대로 배포). null이면 전부 자동선택.
        Map<String, String> partOverrides
) {
}
