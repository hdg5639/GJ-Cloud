package gj.cloud.ops.application.preview.blueprint.composition;

import gj.cloud.ops.application.preview.blueprint.composition.BlueprintCompositionModels.BlueprintCandidateOption;
import gj.cloud.ops.application.preview.blueprint.composition.BlueprintCompositionModels.BlueprintExclusiveGroup;
import gj.cloud.ops.application.preview.blueprint.composition.BlueprintCompositionModels.SelectionMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DiversityAwareSelectionStrategyTest {

    @Test
    void appliesRequestLocalAndHistoricalUsageSignalsWithoutModelDependency() {
        BlueprintUsageTracker tracker = new BlueprintUsageTracker();
        tracker.record("part-a");
        tracker.record("part-a");
        DiversityAwareSelectionStrategy strategy = new DiversityAwareSelectionStrategy(tracker);

        var selections = strategy.select(
                List.of(group("g1"), group("g2")),
                Map.of()
        );

        assertThat(selections).extracting(selection -> selection.componentId())
                .containsExactly("part-a", "part-b");
        assertThat(selections).allSatisfy(selection ->
                assertThat(selection.source()).isEqualTo("DIVERSITY_POLICY"));
    }

    private BlueprintExclusiveGroup group(String id) {
        return new BlueprintExclusiveGroup(
                id,
                "page-1",
                id,
                "page.main",
                SelectionMode.PICK_ONE,
                "base",
                List.of(
                        new BlueprintCandidateOption("part-a", "family-a", "CARD", null, 1.0, false),
                        new BlueprintCandidateOption("part-b", "family-b", "TABLE", null, 0.94, false)
                )
        );
    }
}
