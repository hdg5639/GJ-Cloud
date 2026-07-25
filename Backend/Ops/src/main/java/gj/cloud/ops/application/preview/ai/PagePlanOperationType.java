package gj.cloud.ops.application.preview.ai;

// GamjaBox_2.0_Key_Features.md §9 "AI 출력도 구조화된 패치로 제한함" — 자유 형식 대신 고정된 operation
// 종류만 허용한다. ADD_PAGE/REMOVE_PAGE/SPLIT_PAGE 등은 Variant Registry/Compiler가 생기는 이후
// 증분에서 필요해지면 추가한다 — 지금은 PageDraft 수준(제목·그룹핑)만 다룬다.
public enum PagePlanOperationType {
    RENAME_PAGE,
    MERGE_PAGES,
    MOVE_CAPABILITY
}
