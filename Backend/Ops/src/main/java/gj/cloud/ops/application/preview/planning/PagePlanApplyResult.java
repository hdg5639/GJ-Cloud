package gj.cloud.ops.application.preview.planning;

import gj.cloud.ops.application.preview.analysis.PageDraft;

import java.util.List;

// errors가 비어있지 않으면 all-or-nothing 원칙에 따라 pages는 무시해야 한다(호출 측이 후보 목록을
// 그대로 써야 함) — auto-preview-design/01-blueprint-schema.md §10 Patch 적용 규칙과 동일한 원칙.
public record PagePlanApplyResult(
        List<PageDraft> pages,
        List<String> decisions,
        List<String> errors
) {
}
