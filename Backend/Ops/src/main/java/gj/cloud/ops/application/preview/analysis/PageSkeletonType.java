package gj.cloud.ops.application.preview.analysis;

// GamjaBox_2.0_Key_Features.md 41절 MVP Skeleton 4종. DASHBOARD는 PageDraftGenerator가 리소스 2개
// 이상일 때 LIST capability들을 개수 카드로 기계적으로 모아 생성한다(PageDraftGenerator 참고).
public enum PageSkeletonType {
    AUTH_PAGE,
    RESOURCE_LIST,
    LIST_DETAIL,
    DASHBOARD
}
