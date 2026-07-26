package gj.cloud.ops.application.preview.flow;

import java.util.List;

// GamjaBox_Auto_Preview_Workflow_Composition_Phase2_Change_Request.md §6 — 페이지 액션 하나가 촉발하는
// 워크플로우 전체(trigger + 순차 실행되는 step 목록). 지금은 이 모델을 만들어낼 생성기가 없다(AI
// Planner의 ADD_FLOW/ASSIGN_FLOW는 WP-4) — 모델+검증기만 먼저 갖춘다.
public record FlowBlueprint(
        String id,
        FlowTrigger trigger,
        List<FlowStep> steps
) {
    public record FlowTrigger(String pageId, String actionId) {
    }
}
