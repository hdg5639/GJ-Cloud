package gj.cloud.ops.application.preview.blueprint.search;

import gj.cloud.ops.application.preview.analysis.Block;
import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.CapabilityType;
import gj.cloud.ops.application.preview.analysis.ResourceCategoryClassifier;
import gj.cloud.ops.application.preview.blueprint.BlueprintPartRegistry.PartKind;
import gj.cloud.ops.application.preview.dto.PreviewAnalyzeRequest.Purpose;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.StageRole;
import gj.cloud.ops.application.preview.blueprint.search.BlueprintSearchModels.BlueprintSearchQuery;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class BlueprintSearchQueryFactory {

    private static final ResourceCategoryClassifier CLASSIFIER = ResourceCategoryClassifier.getInstance();

    public static BlueprintSearchQuery forBlock(
            String serviceDescription,
            Block block,
            PartKind kind,
            Purpose purpose,
            Capability primary,
            List<Capability> capabilities,
            int limit
    ) {
        String text = String.join(" ",
                serviceDescription == null ? "" : serviceDescription,
                primary == null || primary.resourceName() == null ? "" : primary.resourceName(),
                primary == null || primary.action() == null ? "" : primary.action(),
                primary == null || primary.operationId() == null ? "" : primary.operationId());
        Set<String> capabilityIds = capabilities == null ? Set.of() : capabilities.stream()
                .map(Capability::id)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
        return new BlueprintSearchQuery(
                text,
                stageRole(kind, primary),
                kind,
                block.slot(),
                purpose,
                block.mode(),
                primary == null ? null : CLASSIFIER.classify(primary),
                capabilityIds,
                dataShapes(kind),
                "3.0.0",
                primary == null ? null : primary.risk(),
                limit
        );
    }

    private static StageRole stageRole(PartKind kind, Capability primary) {
        return switch (kind) {
            case COLLECTION -> StageRole.DISCOVER;
            case DETAIL, DASHBOARD -> StageRole.INSPECT;
            case ACTIONS -> StageRole.COMMIT;
            case OVERLAY -> primary != null && primary.type() == CapabilityType.DELETE
                    ? StageRole.REVIEW : StageRole.PREPARE;
            case NAVIGATION, LAYOUT, THEME -> StageRole.ENTRY;
            case FEEDBACK -> StageRole.VERIFY;
        };
    }

    private static Set<String> dataShapes(PartKind kind) {
        return switch (kind) {
            case COLLECTION -> Set.of("collection");
            case DETAIL -> Set.of("record");
            case DASHBOARD -> Set.of("collection", "metrics");
            case OVERLAY -> Set.of("form-state");
            case ACTIONS -> Set.of("actions");
            default -> Set.of();
        };
    }

    private BlueprintSearchQueryFactory() {
    }
}
