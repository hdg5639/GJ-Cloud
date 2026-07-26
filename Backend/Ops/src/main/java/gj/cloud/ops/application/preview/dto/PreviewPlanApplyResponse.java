package gj.cloud.ops.application.preview.dto;

import gj.cloud.ops.application.preview.analysis.GenerationMode;
import gj.cloud.ops.application.preview.analysis.PageDraft;
import gj.cloud.ops.application.preview.planning.model.PagePlan;

import java.util.List;

// errors가 비어있지 않으면(all-or-nothing 실패) pages는 요청으로 받은 pages 그대로다 — §17 원칙과
// 동일하게 실패를 성공인 것처럼 포장하지 않는다. 이때 generationMode는 항상 RULE_BASED(아무것도 실제로
// 적용되지 않았으므로 SERVICE_AWARE라고 보고하지 않음). pagePlans도 이 pages와 동일한 기준으로
// 파생된다(Workflow Composition Phase 2 WP-1).
public record PreviewPlanApplyResponse(
        List<PageDraft> pages,
        List<PagePlan> pagePlans,
        List<String> decisions,
        List<String> errors,
        GenerationMode generationMode
) {
}
