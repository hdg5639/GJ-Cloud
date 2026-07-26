package gj.cloud.ops.application.preview.flow;

import java.util.List;
import java.util.Map;

// GamjaBox_Auto_Preview_Workflow_Composition_Phase2_Change_Request.md §6 — 워크플로우 한 단계.
// PagePlanOperation(ai 패키지)과 같은 관례로, 타입마다 실제로 쓰는 필드가 다르고 그 외는 항상 null:
// API_CALL/REFRESH_BINDING → bindingRef(+API_CALL은 input)
// SET_CONTEXT → values
// NAVIGATE → pageId, parameters
// POLL → bindingRef, parameters, until, intervalMs, timeoutSeconds
// WAIT → timeoutSeconds
// CONDITION → condition
// SHOW_SUCCESS/SHOW_ERROR → message
public record FlowStep(
        String id,
        FlowStepType type,
        // ApiBinding 모델(§8, WP-3)이 아직 없어 지금은 문자열 참조로만 들고 있는다.
        String bindingRef,
        // 예: {"body": "$form"}. 값이 "$"로 시작하면 FlowExpression으로 검증된다.
        Map<String, String> input,
        // context key -> 값(표현식 또는 리터럴).
        Map<String, String> values,
        String pageId,
        // 대상 파라미터명 -> 값(표현식 또는 리터럴).
        Map<String, String> parameters,
        // 하나라도 만족하면 종료(OR 의미).
        List<PollCondition> until,
        // POLL 전용, §9 예시의 "intervalMs". FlowExecutionPolicy.MIN_INTERVAL_MS 미만은 거부.
        Integer intervalMs,
        Integer timeoutSeconds,
        // boolean으로 평가될 FlowExpression.
        String condition,
        String message
) {
    // "equals"가 아니라 "equalsValue"인 이유: record 컴포넌트명이 "equals"면 생성되는 접근자
    // equals()가 Object.equals(Object)와 이름이 겹쳐(오버로드는 되지만 시그니처가 달라 합법) 혼란스럽다.
    public record PollCondition(String path, String equalsValue, List<String> in) {
    }
}
