package gj.cloud.ops.application.preview.flow;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Workflow Composition Phase 2 §6/§17의 정적 Workflow 검증기. Runtime 실행 전에 step 상한,
// 참조, 제한 표현식, Polling 정책과 아직 지원하지 않는 step 타입을 차단한다. Flow step은 순차 배열이라
// step-level 순환은 표현할 수 없고, Binding refresh 순환은 PagePlanPatchValidator가 별도로 검사한다.
public final class FlowBlueprintValidator {

    public static List<String> validate(FlowBlueprint flow, Set<String> knownPageIds) {
        List<String> errors = new ArrayList<>();
        if (flow == null) {
            return List.of("FlowBlueprint가 null임");
        }
        if (flow.id() == null || flow.id().isBlank()) {
            errors.add("flow id가 비어있음");
        }
        knownPageIds = knownPageIds == null ? Set.of() : knownPageIds;

        if (flow.steps().size() > FlowExecutionPolicy.MAX_STEPS) {
            errors.add("step 개수가 상한(" + FlowExecutionPolicy.MAX_STEPS + ")을 초과함: " + flow.steps().size());
        }

        if (flow.trigger() != null && flow.trigger().pageId() != null
                && !knownPageIds.contains(flow.trigger().pageId())) {
            errors.add("trigger.pageId가 존재하지 않는 페이지를 가리킴: " + flow.trigger().pageId());
        }

        Set<String> stepIds = new HashSet<>();
        for (FlowStep step : flow.steps()) {
            if (step == null) {
                errors.add("null FlowStep은 허용되지 않음");
                continue;
            }
            if (step.id() == null || step.id().isBlank()) {
                errors.add("step id가 비어있음");
            } else if (!stepIds.add(step.id())) {
                errors.add("중복된 step id: " + step.id());
            }
            errors.addAll(validateStep(step, knownPageIds));
        }
        return errors;
    }

    private static List<String> validateStep(FlowStep step, Set<String> knownPageIds) {
        List<String> errors = new ArrayList<>();
        if (step.type() == null) {
            errors.add((step.id() == null ? "<unknown>" : step.id()) + ": step.type이 비어있음");
            return errors;
        }
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
                validateIntervalMs(errors, step);
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

    // MAX_POLL_COUNT를 별도 산식(timeoutSeconds÷intervalMs)으로 다시 검증하지 않는다 — 이 메서드가
    // intervalMs≥MIN_INTERVAL_MS를, validateTimeoutSeconds가 timeoutSeconds≤MAX_TIMEOUT_SECONDS를
    // 이미 강제하고 두 상수가 정확히 MAX_POLL_COUNT로 나누어떨어지게 맞춰져 있어(FlowExecutionPolicy
    // 주석 참고) 두 검증을 통과하면 poll count는 항상 상한 이하다 — 도달 불가능한 코드를 추가하지 않음.
    private static void validateIntervalMs(List<String> errors, FlowStep step) {
        Integer intervalMs = step.intervalMs();
        if (intervalMs == null || intervalMs <= 0) {
            errors.add(step.id() + "(POLL): intervalMs가 없거나 0 이하");
            return;
        }
        if (intervalMs < FlowExecutionPolicy.MIN_INTERVAL_MS) {
            errors.add(step.id() + "(POLL): intervalMs가 최소값(" + FlowExecutionPolicy.MIN_INTERVAL_MS + "ms) 미만");
        } else if (intervalMs > FlowExecutionPolicy.MAX_INTERVAL_MS) {
            errors.add(step.id() + "(POLL): intervalMs가 최대값(" + FlowExecutionPolicy.MAX_INTERVAL_MS + "ms) 초과");
        }
    }

    private static List<String> validatePollCondition(String stepId, FlowStep.PollCondition condition) {
        List<String> errors = new ArrayList<>();
        if (condition == null) {
            return List.of(stepId + "(POLL): null until 조건은 허용되지 않음");
        }
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
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                errors.add(stepId + ": " + fieldName + " key가 비어있음");
            }
            String value = entry.getValue();
            if (value == null) {
                errors.add(stepId + ": " + fieldName + "." + entry.getKey() + " 값이 null임");
            } else if (FlowExpression.isExpressionLike(value) && FlowExpression.parse(value).isEmpty()) {
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
