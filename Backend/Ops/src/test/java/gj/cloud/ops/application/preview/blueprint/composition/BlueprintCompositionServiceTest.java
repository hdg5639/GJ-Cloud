package gj.cloud.ops.application.preview.blueprint.composition;

import gj.cloud.ops.application.preview.blueprint.composition.BlueprintCompositionModels.BlueprintCandidateOption;
import gj.cloud.ops.application.preview.blueprint.composition.BlueprintCompositionModels.BlueprintExclusiveGroup;
import gj.cloud.ops.application.preview.blueprint.composition.BlueprintCompositionModels.SelectionMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BlueprintCompositionServiceTest {

    @Test
    void locallyReselectsOnlyConflictingGroupsUntilCompositionIsDiverse() {
        BlueprintUsageTracker tracker = new BlueprintUsageTracker();
        BlueprintCompositionService service = new BlueprintCompositionService(
                new DiversityAwareSelectionStrategy(tracker), tracker);
        var groups = List.of(group("g1"), group("g2"), group("g3"));

        var result = service.compose(groups, Map.of(
                "g1", "modal-a",
                "g2", "modal-a",
                "g3", "modal-a"
        ));

        assertThat(result.valid()).isTrue();
        assertThat(result.selectionByGroup()).containsEntry("g1", "modal-a");
        assertThat(result.selectionByGroup().values()).doesNotHaveDuplicates();
        assertThat(result.reselectedGroupIds()).containsExactlyInAnyOrder("g2", "g3");
        assertThat(result.findings()).noneMatch(finding ->
                finding.code().startsWith("REPEATED_"));
        assertThat(result.strategy()).isEqualTo("diversity-aware-v1");
        result.selections().forEach(selection ->
                assertThat(tracker.frequency(selection.componentId())).isPositive());
    }

    private BlueprintExclusiveGroup group(String id) {
        return new BlueprintExclusiveGroup(
                id,
                "page-1",
                id,
                "page.overlay",
                SelectionMode.PICK_ONE,
                "modal-a",
                List.of(
                        option("modal-a", "modal", "MODAL", "center", 1.0),
                        option("drawer-b", "drawer", "DRAWER", "side", 0.94),
                        option("inline-c", "inline", "SECTION", "inline", 0.88)
                )
        );
    }

    private BlueprintCandidateOption option(
            String componentId,
            String family,
            String implementationKind,
            String presentation,
            double rank
    ) {
        return new BlueprintCandidateOption(
                componentId, family, implementationKind, presentation, rank, false);
    }
}
