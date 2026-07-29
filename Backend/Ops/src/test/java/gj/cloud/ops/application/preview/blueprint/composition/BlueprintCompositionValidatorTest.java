package gj.cloud.ops.application.preview.blueprint.composition;

import gj.cloud.ops.application.preview.blueprint.composition.BlueprintCompositionModels.BlueprintCandidateOption;
import gj.cloud.ops.application.preview.blueprint.composition.BlueprintCompositionModels.BlueprintExclusiveGroup;
import gj.cloud.ops.application.preview.blueprint.composition.BlueprintCompositionModels.BlueprintSelection;
import gj.cloud.ops.application.preview.blueprint.composition.BlueprintCompositionModels.FindingSeverity;
import gj.cloud.ops.application.preview.blueprint.composition.BlueprintCompositionModels.SelectionMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BlueprintCompositionValidatorTest {

    @Test
    void reportsRepeatedPartsAndOnlyMarksLaterGroupsForReselection() {
        var groups = List.of(
                group("g1", SelectionMode.PICK_ONE),
                group("g2", SelectionMode.PICK_ONE),
                group("g3", SelectionMode.PICK_ONE)
        );
        var selections = List.of(
                selection("g1", "modal-a"),
                selection("g2", "modal-a"),
                selection("g3", "modal-a")
        );

        var findings = BlueprintCompositionValidator.validate(groups, selections);

        assertThat(findings).anySatisfy(finding -> {
            assertThat(finding.code()).isEqualTo("REPEATED_COMPONENT");
            assertThat(finding.groupIds()).containsExactly("g1", "g2", "g3");
            assertThat(finding.reselectableGroupIds()).containsExactly("g2", "g3");
        });
        assertThat(findings).anyMatch(finding ->
                finding.code().equals("REPEATED_OVERLAY_PATTERN"));
    }

    @Test
    void enforcesSingleAndMultiSelectionCardinalityAndCandidateMembership() {
        var groups = List.of(
                group("single", SelectionMode.PICK_ONE),
                group("optional", SelectionMode.OPTIONAL_ONE),
                group("many", SelectionMode.PICK_MANY),
                group("ordered", SelectionMode.ORDER_MANY)
        );
        var selections = List.of(
                selection("single", "modal-a"),
                selection("single", "drawer-b"),
                selection("many", "modal-a"),
                selection("many", "drawer-b"),
                selection("ordered", "not-a-candidate")
        );

        var findings = BlueprintCompositionValidator.validate(groups, selections);

        assertThat(findings).anyMatch(finding ->
                finding.code().equals("EXCLUSIVE_GROUP_OVERSELECTED"));
        assertThat(findings).anyMatch(finding ->
                finding.code().equals("CANDIDATE_OUT_OF_GROUP"));
        assertThat(findings).noneMatch(finding ->
                finding.code().equals("MISSING_SELECTION")
                        && finding.groupIds().contains("optional"));
        assertThat(findings).allSatisfy(finding ->
                assertThat(finding.severity()).isIn(FindingSeverity.WARNING, FindingSeverity.ERROR));
    }

    @Test
    void detectsARepeatedLayoutAcrossTheWholeComposition() {
        var groups = List.of(
                layoutGroup("page-1/layout", "page-1"),
                layoutGroup("page-2/layout", "page-2"),
                layoutGroup("page-3/layout", "page-3")
        );
        var selections = groups.stream()
                .map(group -> selection(group.id(), "workspace-layout"))
                .toList();

        var findings = BlueprintCompositionValidator.validate(groups, selections);

        assertThat(findings).anySatisfy(finding -> {
            assertThat(finding.code()).isEqualTo("REPEATED_LAYOUT_PATTERN");
            assertThat(finding.reselectableGroupIds()).containsExactly("page-3/layout");
        });
    }

    private BlueprintExclusiveGroup group(String id, SelectionMode mode) {
        return new BlueprintExclusiveGroup(
                id,
                "page-1",
                id,
                "page.overlay",
                mode,
                "modal-a",
                List.of(
                        option("modal-a", "modal", "MODAL", "center"),
                        option("drawer-b", "drawer", "DRAWER", "side")
                )
        );
    }

    private BlueprintCandidateOption option(
            String componentId,
            String family,
            String implementationKind,
            String presentation
    ) {
        return new BlueprintCandidateOption(
                componentId, family, implementationKind, presentation, 1.0, false);
    }

    private BlueprintExclusiveGroup layoutGroup(String id, String pageId) {
        return new BlueprintExclusiveGroup(
                id,
                pageId,
                "layout",
                "page.layout",
                SelectionMode.PICK_ONE,
                "default-layout",
                List.of(
                        option("workspace-layout", "workspace", "LAYOUT", null),
                        option("catalog-layout", "catalog", "LAYOUT", null)
                )
        );
    }

    private BlueprintSelection selection(String groupId, String componentId) {
        return new BlueprintSelection(groupId, componentId, "TEST");
    }
}
