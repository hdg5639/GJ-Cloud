package gj.cloud.ops.application.preview.dto;

import gj.cloud.ops.application.preview.analysis.AuthStrategy;
import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.GenerationMode;
import gj.cloud.ops.application.preview.analysis.PageDraft;
import gj.cloud.ops.application.preview.binding.ApiBinding;
import gj.cloud.ops.application.preview.flow.FlowBlueprint;
import gj.cloud.ops.application.preview.planning.model.PagePlan;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

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
        GenerationMode generationMode
) {
}
