package gj.cloud.ops.application.preview.dto;

import gj.cloud.ops.application.preview.analysis.GenerationMode;
import gj.cloud.ops.application.preview.analysis.PageDraft;
import gj.cloud.ops.application.preview.binding.ApiBinding;
import gj.cloud.ops.application.preview.flow.FlowBlueprint;
import gj.cloud.ops.application.preview.planning.model.PagePlan;

import java.util.List;

// WP-4 계획 Patch 적용 결과. errors가 있으면 all-or-nothing으로 원본 PagePlan/Flow/Binding 정본을
// 그대로 반환하고 RULE_BASED로 보고한다. 성공하면 사용자 승인 operation이 반영된 정본과 결정 기록을
// 반환하며, 이 상태가 Block 재컴파일·배포 요청·Blueprint Snapshot까지 그대로 전달된다.
public record PreviewPlanApplyResponse(
        List<PageDraft> pages,
        List<PagePlan> pagePlans,
        List<FlowBlueprint> flows,
        List<ApiBinding> bindings,
        List<String> decisions,
        List<String> errors,
        GenerationMode generationMode
) {
}
