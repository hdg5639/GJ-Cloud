package gj.cloud.ops.application.preview.layout;

import java.util.List;

// GamjaBox_Auto_Preview_Workflow_Composition_Phase2_Change_Request.md §10 — 재사용 가능한 페이지
// 셸(shell) 구조. WP-1이 만든 PagePlan.layoutRef가 지금까지 항상 null이었던 이유가 이 모델이 없어서
// 였다 — 이 모델+LayoutBlueprints 레지스트리가 그 자리를 처음 채운다.
//
// 의도적으로 아직 안 하는 것: purpose별로 실제 셸 컴포넌트(사이드바 내비게이션 vs 컴팩트 툴바 vs
// 제품형 내비게이션, §10 "Purpose-specific shell behavior")를 렌더링하는 프론트 작업, 그리고 이
// Blueprint로 Block 배치를 검증하는 LayoutBlueprintValidator(SlotCardinalityValidator가 이미
// SkeletonSlotContracts로 이 역할을 하고 있어 중복 검증기를 만들지 않음) — 둘 다 이 모델을 실제
// 소비하는 다음 조각의 몫으로 명시적으로 미룬다(WP-1~3과 같은 "모델 먼저" 패턴).
public record LayoutBlueprint(String id, String version, List<LayoutSlot> slots) {
}
