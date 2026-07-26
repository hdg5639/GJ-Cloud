package gj.cloud.ops.application.preview.flow;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class FlowBlueprintValidatorTest {

    private static final Set<String> KNOWN_PAGE_IDS = Set.of("vm-list", "vm-detail");

    // §6의 vm-create-flow 예시(API_CALL→SET_CONTEXT→POLL→NAVIGATE)와 동일한 형태 — 실제로 통과해야
    // 문서가 요구하는 시나리오를 이 모델이 표현할 수 있다는 증거가 된다.
    @Test
    void vmCreateFlowShapedBlueprintHasNoErrors() {
        FlowBlueprint flow = new FlowBlueprint(
                "vm-create-flow",
                new FlowBlueprint.FlowTrigger("vm-list", "create-vm"),
                List.of(
                        new FlowStep("submit-vm", FlowStepType.API_CALL, "vm-create-binding",
                                Map.of("body", "$form"), null, null, null, null, null, null, null),
                        new FlowStep("save-vm-id", FlowStepType.SET_CONTEXT, null, null,
                                Map.of("vmId", "$steps.submit-vm.response.data.id"), null, null, null, null, null, null),
                        new FlowStep("track-creation", FlowStepType.POLL, "vm-detail-binding", null, null, null,
                                Map.of("vmId", "$context.vmId"),
                                List.of(new FlowStep.PollCondition("data.status", "RUNNING", null)),
                                180, null, null),
                        new FlowStep("open-detail", FlowStepType.NAVIGATE, null, null, null, "vm-detail",
                                Map.of("vmId", "$context.vmId"), null, null, null, null)
                )
        );

        assertThat(FlowBlueprintValidator.validate(flow, KNOWN_PAGE_IDS)).isEmpty();
    }

    @Test
    void unknownTriggerAndNavigatePageIdsAreErrors() {
        FlowBlueprint flow = new FlowBlueprint(
                "flow-1",
                new FlowBlueprint.FlowTrigger("unknown-page", "action"),
                List.of(new FlowStep("nav", FlowStepType.NAVIGATE, null, null, null, "also-unknown",
                        null, null, null, null, null))
        );

        List<String> errors = FlowBlueprintValidator.validate(flow, KNOWN_PAGE_IDS);

        assertThat(errors).anyMatch(e -> e.contains("trigger.pageId") && e.contains("unknown-page"));
        assertThat(errors).anyMatch(e -> e.contains("also-unknown"));
    }

    @Test
    void duplicateStepIdsAreAnError() {
        FlowBlueprint flow = new FlowBlueprint("flow-1", null, List.of(
                new FlowStep("dup", FlowStepType.SHOW_SUCCESS, null, null, null, null, null, null, null, null, "ok"),
                new FlowStep("dup", FlowStepType.SHOW_SUCCESS, null, null, null, null, null, null, null, null, "ok")
        ));

        assertThat(FlowBlueprintValidator.validate(flow, KNOWN_PAGE_IDS))
                .anyMatch(e -> e.contains("중복된 step id"));
    }

    @Test
    void missingRequiredFieldsPerStepTypeAreErrors() {
        FlowBlueprint flow = new FlowBlueprint("flow-1", null, List.of(
                new FlowStep("call", FlowStepType.API_CALL, null, null, null, null, null, null, null, null, null),
                new FlowStep("ctx", FlowStepType.SET_CONTEXT, null, null, null, null, null, null, null, null, null),
                new FlowStep("cond", FlowStepType.CONDITION, null, null, null, null, null, null, null, null, null),
                new FlowStep("wait", FlowStepType.WAIT, null, null, null, null, null, null, null, null, null),
                new FlowStep("success", FlowStepType.SHOW_SUCCESS, null, null, null, null, null, null, null, null, null)
        ));

        List<String> errors = FlowBlueprintValidator.validate(flow, KNOWN_PAGE_IDS);

        assertThat(errors).anyMatch(e -> e.startsWith("call: bindingRef가 비어있음"));
        assertThat(errors).anyMatch(e -> e.contains("SET_CONTEXT): values가 비어있음"));
        assertThat(errors).anyMatch(e -> e.startsWith("cond: condition가 비어있음"));
        assertThat(errors).anyMatch(e -> e.contains("WAIT): timeoutSeconds가 없거나 0 이하"));
        assertThat(errors).anyMatch(e -> e.startsWith("success: message가 비어있음"));
    }

    @Test
    void pollTimeoutExceedingPolicyMaxIsAnError() {
        FlowStep poll = new FlowStep("poll", FlowStepType.POLL, "binding", null, null, null, null,
                List.of(new FlowStep.PollCondition("status", "DONE", null)),
                FlowExecutionPolicy.MAX_TIMEOUT_SECONDS + 1, null, null);
        FlowBlueprint flow = new FlowBlueprint("flow-1", null, List.of(poll));

        assertThat(FlowBlueprintValidator.validate(flow, KNOWN_PAGE_IDS))
                .anyMatch(e -> e.contains("timeoutSeconds가 상한"));
    }

    @Test
    void pollConditionMustHaveExactlyOneOfEqualsOrIn() {
        FlowStep neither = new FlowStep("poll1", FlowStepType.POLL, "binding", null, null, null, null,
                List.of(new FlowStep.PollCondition("status", null, null)), 60, null, null);
        FlowStep both = new FlowStep("poll2", FlowStepType.POLL, "binding", null, null, null, null,
                List.of(new FlowStep.PollCondition("status", "DONE", List.of("DONE", "FAILED"))), 60, null, null);

        assertThat(FlowBlueprintValidator.validate(new FlowBlueprint("f1", null, List.of(neither)), KNOWN_PAGE_IDS))
                .anyMatch(e -> e.contains("equalsValue/in 중 정확히 하나만"));
        assertThat(FlowBlueprintValidator.validate(new FlowBlueprint("f2", null, List.of(both)), KNOWN_PAGE_IDS))
                .anyMatch(e -> e.contains("equalsValue/in 중 정확히 하나만"));
    }

    @Test
    void disallowedExpressionSyntaxInStepFieldsIsAnError() {
        FlowStep step = new FlowStep("call", FlowStepType.API_CALL, "binding",
                Map.of("body", "$form.name.toUpperCase()"), null, null, null, null, null, null, null);

        assertThat(FlowBlueprintValidator.validate(new FlowBlueprint("f1", null, List.of(step)), KNOWN_PAGE_IDS))
                .anyMatch(e -> e.contains("허용되지 않는 표현식"));
    }

    @Test
    void deferredStepTypesAreExplicitlyRejected() {
        FlowStep step = new FlowStep("stream", FlowStepType.EVENT_STREAM, null, null, null, null, null,
                null, null, null, null);

        assertThat(FlowBlueprintValidator.validate(new FlowBlueprint("f1", null, List.of(step)), KNOWN_PAGE_IDS))
                .anyMatch(e -> e.contains("아직 지원하지 않는 step 타입"));
    }

    @Test
    void stepCountExceedingPolicyMaxIsAnError() {
        List<FlowStep> steps = IntStream.rangeClosed(1, FlowExecutionPolicy.MAX_STEPS + 1)
                .mapToObj(i -> new FlowStep("s" + i, FlowStepType.SHOW_SUCCESS, null, null, null, null, null,
                        null, null, null, "ok"))
                .toList();

        assertThat(FlowBlueprintValidator.validate(new FlowBlueprint("f1", null, steps), KNOWN_PAGE_IDS))
                .anyMatch(e -> e.contains("step 개수가 상한"));
    }
}
