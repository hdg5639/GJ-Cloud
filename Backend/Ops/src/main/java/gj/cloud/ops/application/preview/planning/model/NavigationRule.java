package gj.cloud.ops.application.preview.planning.model;

import java.util.Map;

// GamjaBox_Auto_Preview_Workflow_Composition_Phase2_Change_Request.md §7 — 지금의 selectedRow 상태
// 기반 상세 진입을 대체할 명시적 네비게이션 규칙. PagePlanMapper는 아직 이 필드를 채우지 않는다(§7
// Navigation은 §22 우선순위 4번 — PagePlan 모델 자체가 먼저 있어야 다음 작업이 참조할 자리가 생긴다).
public record NavigationRule(
        String sourcePageId,
        // 열린 문자열 — 문서 예시가 "row.select" 하나뿐이라 지금은 트리거 종류를 못박지 않는다.
        String trigger,
        NavigationType type,
        String targetPageId,
        // 예: {"vmId": "$row.id"} — 실제 표현식 해석은 §6/§8(API Result Chaining)의 몫.
        Map<String, String> parameters
) {
    // §7 "Required navigation types" 그대로.
    public enum NavigationType {
        OPEN_PAGE,
        OPEN_OVERLAY,
        GO_BACK,
        REPLACE_ROUTE
    }
}
