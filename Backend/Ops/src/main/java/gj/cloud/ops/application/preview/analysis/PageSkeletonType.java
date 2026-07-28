package gj.cloud.ops.application.preview.analysis;

// PageDraft는 기존 CRUD fallback/Block Resolver 호환 모델이다. RESOURCE_DETAIL은 PagePlan의 독립 상세
// 페이지를 기존 렌더링 경로로 손실 없이 내리기 위해 추가했다. 새 의미 모델의 정본은 PagePlan이다.
public enum PageSkeletonType {
    AUTH_PAGE,
    RESOURCE_LIST,
    RESOURCE_DETAIL,
    LIST_DETAIL,
    DASHBOARD
}
