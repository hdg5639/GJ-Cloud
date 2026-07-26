package gj.cloud.ops.application.preview.dto;

import gj.cloud.ops.application.preview.ai.PagePlanOperation;
import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.PageDraft;
import gj.cloud.ops.application.preview.binding.ApiBinding;
import gj.cloud.ops.application.preview.flow.FlowBlueprint;
import gj.cloud.ops.application.preview.planning.model.PagePlan;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

// 사용자가 선택한 Patch를 현재 PagePlan/Flow/Binding 정본에 결정론적으로 적용한다. pages는 기존
// Block/클라이언트 호환용이며 pagePlans가 비어있을 때만 fallback 원본으로 사용한다.
public record PreviewPlanApplyRequest(
        @NotEmpty List<Capability> capabilities,
        @NotEmpty List<PageDraft> pages,
        List<PagePlan> pagePlans,
        List<FlowBlueprint> flows,
        List<ApiBinding> bindings,
        @NotNull List<PagePlanOperation> operations
) {
}
