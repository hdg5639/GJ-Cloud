package gj.cloud.ops.application.preview.blueprint.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import gj.cloud.ops.application.preview.analysis.RiskLevel;
import gj.cloud.ops.application.preview.blueprint.BlueprintCategory;
import gj.cloud.ops.application.preview.blueprint.BlueprintPartRegistry.PartKind;
import gj.cloud.ops.application.preview.dto.PreviewAnalyzeRequest.Purpose;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.StageRole;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BlueprintRetrievalServiceTest {

    @Test
    void disabledElasticsearchUsesSameHardFilteredRegistryAndReturnsDiagnostics() {
        BlueprintSearchProperties properties = new BlueprintSearchProperties(
                false, "http://localhost:9200", "gamjabox-blueprints-test", "", "", false);
        ElasticsearchBlueprintIndex index = new ElasticsearchBlueprintIndex(properties, new ObjectMapper());
        BlueprintRetrievalService service = new BlueprintRetrievalService(properties, index);
        var query = new BlueprintSearchModels.BlueprintSearchQuery(
                "customer directory profiles",
                StageRole.DISCOVER,
                PartKind.COLLECTION,
                "page.main",
                Purpose.PRODUCT_LIKE,
                null,
                BlueprintCategory.CRM,
                Set.of(),
                Set.of("collection"),
                "3.0.0",
                RiskLevel.SAFE,
                5
        );

        var result = service.search(query);

        assertThat(result.candidates()).isNotEmpty().hasSizeLessThanOrEqualTo(5);
        assertThat(result.candidates()).allSatisfy(candidate -> {
            assertThat(candidate.metadata().mountPoint()).isEqualTo(PartKind.COLLECTION);
            assertThat(candidate.metadata().acceptedSurfaces()).contains("page.main");
            assertThat(candidate.metadata().supportedStages()).contains(StageRole.DISCOVER);
        });
        assertThat(result.diagnostics().engine()).isEqualTo("registry");
        assertThat(result.diagnostics().fallbackUsed()).isTrue();
        assertThat(result.diagnostics().registryCount()).isEqualTo(service.registryDocuments().size());
        assertThat(result.diagnostics().rejectionCounts()).isNotEmpty();
    }

    @Test
    void reindexIsSafeNoopWhenFeatureIsDisabled() {
        BlueprintSearchProperties properties = new BlueprintSearchProperties(
                false, null, "gamjabox-blueprints-test", null, null, false);
        BlueprintRetrievalService service = new BlueprintRetrievalService(
                properties, new ElasticsearchBlueprintIndex(properties, new ObjectMapper()));

        var result = service.reindex();

        assertThat(result.succeeded()).isFalse();
        assertThat(result.indexedCount()).isZero();
    }
}
