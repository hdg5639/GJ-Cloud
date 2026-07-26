package gj.cloud.ops.application.preview.planning.model;

// GamjaBox_Auto_Preview_Workflow_Composition_Phase2_Change_Request.md §5 "Minimum page types".
// 지금 결정론적 생성기(RuleBasedPagePlanGenerator/PageDraftGenerator)는 PageSkeletonType 4종만
// 만들어서 AUTH/DASHBOARD/RESOURCE_LIST/LIST_DETAIL만 실제로 매핑된다(PagePlanMapper 참고) — 나머지
// 7종은 향후 AI Planner의 SET_PAGE_TYPE 오퍼레이션(§12, WP-4)이 채울 자리로 미리 선언만 해둔다.
public enum PageType {
    AUTH,
    DASHBOARD,
    RESOURCE_LIST,
    RESOURCE_OVERVIEW,
    RESOURCE_DETAIL,
    LIST_DETAIL,
    WORKFLOW,
    SETTINGS,
    ACTIVITY,
    FILE_MANAGER,
    ORGANIZATION
}
