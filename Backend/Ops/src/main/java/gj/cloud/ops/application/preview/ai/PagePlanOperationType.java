package gj.cloud.ops.application.preview.ai;

// GamjaBox_2.0_Key_Features.md §9 "AI 출력도 구조화된 패치로 제한함" — 자유 형식 대신 고정된 operation
// 종류만 허용한다. Workflow Composition Phase 2 Change Request §12 WP-4가 요구하는 9종
// (ADD_PAGE/REMOVE_PAGE/SPLIT_PAGE/SET_PAGE_TYPE/SET_LAYOUT/SET_FEATURE/ADD_NAVIGATION/ADD_FLOW/
// ASSIGN_FLOW) 중 ADD_PAGE/REMOVE_PAGE만 이번 조각에서 추가한다 — 둘 다 기존 PagePlanOperation의
// pageId/newTitle 필드만으로 표현 가능하고(REMOVE는 pageId만), MOVE_CAPABILITY와 조합하면 SPLIT_PAGE와
// 동등한 효과를 낼 수 있어(ADD_PAGE로 빈 페이지를 만들고 MOVE_CAPABILITY 여러 번으로 채움) SPLIT_PAGE는
// 별도 오퍼레이션으로 안 만들었다. SET_PAGE_TYPE/SET_LAYOUT/SET_FEATURE/ADD_NAVIGATION은 PagePlan이
// 지금 PageDraft로부터 매 요청마다 새로 파생되는 순수 함수(PagePlanMapper)라 "덮어쓴 값"을 저장할 데가
// 없어서(다음 analyze/plan 호출에서 그대로 사라짐) 막혀있다 — PagePlan을 영속 상태로 바꾸는 더 큰
// 아키텍처 결정이 먼저 필요. ADD_FLOW/ASSIGN_FLOW는 FlowBlueprint의 중첩 구조(steps 배열 등)를
// AI structured output 스키마로 어떻게 표현할지 별도 설계가 필요해 이번 조각 범위 밖.
public enum PagePlanOperationType {
    RENAME_PAGE,
    MERGE_PAGES,
    MOVE_CAPABILITY,
    ADD_PAGE,
    REMOVE_PAGE
}
