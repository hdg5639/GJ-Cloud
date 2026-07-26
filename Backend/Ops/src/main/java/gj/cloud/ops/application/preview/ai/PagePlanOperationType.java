package gj.cloud.ops.application.preview.ai;

// Workflow Composition Phase 2 Change Request §12 WP-4 — AI가 자유 형식 Blueprint를 쓰지 않고
// 이 고정 operation 집합만 제안한다. 모든 operation은 PagePlanPatchValidator를 거쳐야 실제 상태에
// 반영되며, 최종 적용 단계에서는 PagePlan/Flow/Binding 전체 완성도까지 다시 검증한다.
public enum PagePlanOperationType {
    RENAME_PAGE,
    MERGE_PAGES,
    MOVE_CAPABILITY,
    ADD_PAGE,
    REMOVE_PAGE,
    SPLIT_PAGE,
    SET_PAGE_TYPE,
    SET_LAYOUT,
    SET_FEATURE,
    ADD_NAVIGATION,
    ADD_FLOW,
    ASSIGN_FLOW
}
