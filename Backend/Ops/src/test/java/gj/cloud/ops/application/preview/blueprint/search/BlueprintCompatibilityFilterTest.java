package gj.cloud.ops.application.preview.blueprint.search;

import gj.cloud.ops.application.preview.analysis.RiskLevel;
import gj.cloud.ops.application.preview.blueprint.BlueprintCategory;
import gj.cloud.ops.application.preview.blueprint.BlueprintPartRegistry.PartKind;
import gj.cloud.ops.application.preview.dto.PreviewAnalyzeRequest.Purpose;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.StageRole;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BlueprintCompatibilityFilterTest {

    @Test
    void removesCandidatesBeforeRetrievalWhenStageOrShapeIsIncompatible() {
        var documents = BlueprintRegistryIndexProjection.projectAll();
        var query = query(StageRole.COMMIT, PartKind.COLLECTION, Set.of("collection"), RiskLevel.SAFE);

        var result = BlueprintCompatibilityFilter.filter(documents, query);

        assertThat(result.compatible()).isEmpty();
        assertThat(result.rejectionCounts()).containsKey("stage");
    }

    @Test
    void destructiveOverlayOnlyKeepsBlueprintsWithExplicitSafetyPresentation() {
        var documents = BlueprintRegistryIndexProjection.projectAll();
        var query = query(StageRole.REVIEW, PartKind.OVERLAY, Set.of("form-state"), RiskLevel.DESTRUCTIVE);

        var result = BlueprintCompatibilityFilter.filter(documents, query);

        assertThat(result.compatible()).isNotEmpty();
        assertThat(result.compatible()).allSatisfy(document ->
                assertThat(document.presentationTags()).anyMatch(tag ->
                        tag.contains("danger") || tag.contains("confirm") || tag.contains("impact")
                                || tag.contains("approval") || tag.contains("destructive")));
        assertThat(result.rejectionCounts()).containsKey("risk_policy");
    }

    private BlueprintSearchModels.BlueprintSearchQuery query(
            StageRole stage,
            PartKind kind,
            Set<String> shapes,
            RiskLevel risk
    ) {
        return new BlueprintSearchModels.BlueprintSearchQuery(
                "customer management", stage, kind, "page.main", Purpose.PRODUCT_LIKE,
                null, BlueprintCategory.CRM, Set.of(), shapes, "3.0.0", risk, 8);
    }
}
