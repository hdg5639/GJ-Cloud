package gj.cloud.ops.application.preview.dto;

import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.PageDraft;
import gj.cloud.ops.application.preview.binding.ApiBinding;
import gj.cloud.ops.application.preview.flow.FlowBlueprint;
import gj.cloud.ops.application.preview.planning.model.PagePlan;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

// /plan/propose는 PageDraft뿐 아니라 현재 PagePlan/Flow/Binding 정본을 함께 받아야 SET_LAYOUT,
// ADD_NAVIGATION, ADD_FLOW 같은 제안이 기존 사용자 수정 상태 위에 누적될 수 있다. pagePlans가 없는
// 구버전 클라이언트는 Controller에서 pages를 PagePlanMapper로 변환해 호환한다.
public record PreviewPlanRequest(
        @Size(max = 2000) String serviceDescription,
        PreviewAnalyzeRequest.Purpose purpose,
        @NotEmpty List<Capability> capabilities,
        @NotEmpty List<PageDraft> pages,
        List<PagePlan> pagePlans,
        List<FlowBlueprint> flows,
        List<ApiBinding> bindings
) {
}
