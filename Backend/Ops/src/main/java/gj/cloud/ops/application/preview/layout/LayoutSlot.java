package gj.cloud.ops.application.preview.layout;

import gj.cloud.ops.application.preview.analysis.Cardinality;

// GamjaBox_Auto_Preview_Workflow_Composition_Phase2_Change_Request.md §10 — LayoutBlueprint 하나의
// Slot 선언. Cardinality는 SkeletonSlotContracts(page.main/page.aside 등, PageSkeletonType 4종 전용)
// 가 이미 쓰던 것과 같은 enum을 재사용한다 — 같은 개념(Slot 하나에 Block이 몇 개 들어올 수 있는지)을
// 두 번 정의하지 않는다.
public record LayoutSlot(String name, Cardinality cardinality) {
}
