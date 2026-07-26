package gj.cloud.ops.application.preview.planning.model;

// Workflow Composition Phase 2 §5의 최소 페이지 타입. 규칙 기반 Mapper가 기본 타입을 만들고,
// WP-4 SET_PAGE_TYPE/SPLIT_PAGE가 의미 기반 타입과 독립 RESOURCE_DETAIL을 실제 계획에 적용한다.
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
