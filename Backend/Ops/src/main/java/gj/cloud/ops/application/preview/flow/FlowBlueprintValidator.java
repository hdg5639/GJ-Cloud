package gj.cloud.ops.application.preview.flow;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// GamjaBox_Auto_Preview_Workflow_Composition_Phase2_Change_Request.md §6/§17 — PagePlanValidator와
// 같은 static 유틸리티 관례. 아직 "적용"할 대상이 없어(FlowExecutor 없음) PagePlanApplyResult 같은
// Result 레코드로 감싸지 않고 에러 문자열 목록만 반환한다.
//
// 순환 탐지(§17)는 이번 조각 범위 밖 — 지금 모델에는 step 간 "다음 step으로 분기" 링크가 없어(순서
// 그대로 순차 실행이 유일한 흐름) 순환이 발생할 자리 자체가 없다. 여러 Flow/Page가 서로를 참조해
// 순환이 생기는 경우(AI의 ADD_FLOW/ASSIGN_FLOW, Navigation 그래프)는 그 참조 자체가 아직 없어서
// WP-4/Navigation 도입 시 처리한다.
public final class FlowBlueprintValidator {

    public static List<String> validate(FlowBlueprint flow, Set<String> knownPageIds) {
        List<String> errors = new ArrayList<>();

        if (flow.steps().size() > FlowExecutionPolicy.MAX_STEPS) {
            errors.add("step 개수가 상한(" + FlowExecutionPolicy.MAX_STEPS + ")을 초과함: " + flow.steps().size());
        }

        if (flow.trigger() != null && flow.trigger().pageId() != null
                && !knownPageIds.contains(flow.trigger().pageId())) {
            errors.add("trigger.pageId가 존재하지 않는 페이지를 가리킴: " + flow.trigger().pageId());
        }

        Set<String> stepIds = new HashSet<>();
        for (FlowStep step : flow.steps()) {
            if (!stepIds.add(step.id())) {
                errors.add("중복된 step id: " + step.id());
            }
            errors.addAll(validateStep(step, knownPageIds));
        }
        return errors;
    }

    private static List<String> validateStep(FlowStep step, Set<String> knownPageIds) {
        List<String> errors = new ArrayList<>();
        switch (step.type()) {
            case API_CALL, REFRESH_BINDING -> requireNonBlank(errors, step.id(), "bindingRef", step.bindingRef());
            case SET_CONTEXT -> {
                if (step.values() == null || step.values().isEmpty()) {
                    errors.add(step.id() + "(SET_CONTEXT): values가 비어있음");
                }
            }
            case NAVIGATE -> {
                requireNonBlank(errors, step.id(), "pageId", step.pageId());
                if (step.pageId() != null && !knownPageIds.contains(step.pageId())) {
                    errors.add(step.id() + "(NAVIGATE): 존재하지 않는 pageId(" + step.pageId() + ")");
                }
            }
            case POLL -> {
                requireNonBlank(errors, step.id(), "bindingRef", step.bindingRef());
                if (step.until() == null || step.until().isEmpty()) {
                    errors.add(step.id() + "(POLL): until 조건이 비어있음");
                } else {
                    for (FlowStep.PollCondition condition : step.until()) {
                        errors.addAll(validatePollCondition(step.id(), condition));
                    }
                }
                validateTimeoutSeconds(errors, step);
            }
            case WAIT -> validateTimeoutSeconds(errors, step);
            case CONDITION -> requireNonBlank(errors, step.id(), "condition", step.condition());
            case SHOW_SUCCESS, SHOW_ERROR -> requireNonBlank(errors, step.id(), "message", step.message());
            case EVENT_STREAM, UPLOAD, DOWNLOAD, PARALLEL ->
                    errors.add(step.id() + "(" + step.type() + "): 아직 지원하지 않는 step 타입(§6 \"Deferred but reserved\")");
        }
        errors.addAll(validateExpressions(step));
        return errors;
    }

    private static void validateTimeoutSeconds(List<String> errors, FlowStep step) {
        if (step.timeoutSeconds() == null || step.timeoutSeconds() <= 0) {
            errors.add(step.id() + "(" + step.type() + "): timeoutSeconds가 없거나 0 이하");
        } else if (step.timeoutSeconds() > FlowExecutionPolicy.MAX_TIMEOUT_SECONDS) {
            errors.add(step.id() + "(" + step.type() + "): timeoutSeconds가 상한("
                    + FlowExecutionPolicy.MAX_TIMEOUT_SECONDS + "초) 초과");
        }
    }

    private static List<String> validatePollCondition(String stepId, FlowStep.PollCondition condition) {
        List<String> errors = new ArrayList<>();
        if (condition.path() == null || condition.path().isBlank()) {
            errors.add(stepId + "(POLL): until 조건의 path가 비어있음");
        }
        boolean hasEquals = condition.equalsValue() != null;
        boolean hasIn = condition.in() != null && !condition.in().isEmpty();
        if (hasEquals == hasIn) {
            errors.add(stepId + "(POLL): until 조건은 equalsValue/in 중 정확히 하나만 있어야 함");
        }
        return errors;
    }

    private static List<String> validateExpressions(FlowStep step) {
        List<String> errors = new ArrayList<>();
        validateExpressionMap(errors, step.id(), "input", step.input());
        validateExpressionMap(errors, step.id(), "values", step.values());
        validateExpressionMap(errors, step.id(), "parameters", step.parameters());
        if (step.condition() != null && FlowExpression.isExpressionLike(step.condition())
                && FlowExpression.parse(step.condition()).isEmpty()) {
            errors.add(step.id() + ": condition이 허용되지 않는 표현식 형식임(" + step.condition() + ")");
        }
        return errors;
    }

    private static void validateExpressionMap(List<String> errors, String stepId, String fieldName,
                                               Map<String, String> map) {
        if (map == null) {
            return;
        }
        for (Map.Entry<String, String> entry : map.entrySet()) {
            String value = entry.getValue();
            if (FlowExpression.isExpressionLike(value) && FlowExpression.parse(value).isEmpty()) {
                errors.add(stepId + ": " + fieldName + "." + entry.getKey() + "가 허용되지 않는 표현식 형식임(" + value + ")");
            }
        }
    }

    private static void requireNonBlank(List<String> errors, String stepId, String fieldName, String value) {
        if (value == null || value.isBlank()) {
            errors.add(stepId + ": " + fieldName + "가 비어있음");
        }
    }

    private FlowBlueprintValidator() {
    }
}
