package gj.cloud.ops.application.preview.blueprint.search;

import gj.cloud.ops.application.preview.blueprint.BlueprintPartRegistry;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.StageRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BlueprintRegistryIndexProjectionTest {

    @Test
    void projectsEveryManifestPartIntoRebuildableSearchMetadata() {
        var documents = BlueprintRegistryIndexProjection.projectAll();

        assertThat(documents).hasSameSizeAs(BlueprintPartRegistry.ALL);
        assertThat(documents).extracting(document -> document.blueprintId()).doesNotHaveDuplicates();
        assertThat(documents).allSatisfy(document -> {
            assertThat(document.version()).isEqualTo(BlueprintSearchModels.METADATA_VERSION);
            assertThat(document.runtimeVersion()).isEqualTo(">=3.0.0");
            assertThat(document.status()).isEqualTo(BlueprintSearchModels.BlueprintStatus.ACTIVE);
            assertThat(document.label()).isNotBlank();
            assertThat(document.family()).isNotBlank();
            assertThat(document.acceptedSurfaces()).isNotEmpty();
        });
    }

    @Test
    void derivesStageAndDataShapeContractsFromManifestKind() {
        var byId = BlueprintRegistryIndexProjection.projectAll().stream()
                .collect(java.util.stream.Collectors.toMap(document -> document.blueprintId(), value -> value));

        assertThat(byId.get("entity-directory").supportedStages())
                .contains(StageRole.DISCOVER, StageRole.SELECT);
        assertThat(byId.get("entity-directory").requiredDataShapes()).containsExactly("collection");
        assertThat(byId.get("typed-danger-modal").supportedStages())
                .contains(StageRole.REVIEW, StageRole.COMMIT);
        assertThat(byId.get("typed-danger-modal").presentationTags())
                .anyMatch(tag -> tag.contains("danger"));
    }
}
