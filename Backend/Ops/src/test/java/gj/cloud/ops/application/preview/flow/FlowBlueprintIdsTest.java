package gj.cloud.ops.application.preview.flow;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FlowBlueprintIdsTest {

    @Test
    void repairsAnAlreadyGeneratedDuplicateWithoutChangingItsTriggerOrSteps() {
        FlowStep firstStep = new FlowStep(
                "run-command", FlowStepType.API_CALL, "events.cancel-binding",
                null, null, null, null, null, null, null, null, null);
        FlowStep secondStep = new FlowStep(
                "run-command", FlowStepType.API_CALL, "events.cancel-series-binding",
                null, null, null, null, null, null, null, null, null);
        FlowBlueprint first = new FlowBlueprint(
                "events-page-cancel-flow",
                new FlowBlueprint.FlowTrigger("events-page", "events.cancel"),
                List.of(firstStep));
        FlowBlueprint second = new FlowBlueprint(
                "events-page-cancel-flow",
                new FlowBlueprint.FlowTrigger("events-page", "events.cancel-series"),
                List.of(secondStep));

        List<FlowBlueprint> normalized = FlowBlueprintIds.ensureUnique(List.of(first, second));

        assertThat(normalized).extracting(FlowBlueprint::id).doesNotHaveDuplicates();
        assertThat(normalized.get(0).id()).isEqualTo("events-page-cancel-flow");
        assertThat(normalized.get(1).id()).startsWith("events-page-cancel-flow-");
        assertThat(normalized.get(1).trigger()).isEqualTo(second.trigger());
        assertThat(normalized.get(1).steps()).containsExactly(secondStep);
        assertThat(FlowBlueprintIds.ensureUnique(normalized)).isEqualTo(normalized);
    }
}
