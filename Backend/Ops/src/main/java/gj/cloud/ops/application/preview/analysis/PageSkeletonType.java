package gj.cloud.ops.application.preview.analysis;

// GamjaBox_2.0_Key_Features.md 41절 MVP Skeleton 4종. DASHBOARD는 여러 리소스에 걸친 요약이 필요해
// 규칙만으로 확정하기 어려우므로, MVP의 PageDraftGenerator는 아직 이 값을 생성하지 않는다
// (Phase E 이후 필요해지면 이 enum은 이미 준비되어 있음).
public enum PageSkeletonType {
    AUTH_PAGE,
    RESOURCE_LIST,
    LIST_DETAIL,
    DASHBOARD
}
