package gj.cloud.ops.application.preview.blueprint.search;

import gj.cloud.ops.application.preview.analysis.RiskLevel;
import gj.cloud.ops.application.preview.blueprint.BlueprintCategory;
import gj.cloud.ops.application.preview.blueprint.BlueprintPartRegistry.PartKind;
import gj.cloud.ops.application.preview.dto.PreviewAnalyzeRequest.Purpose;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.StageRole;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BlueprintSearchModels {

    public static final String METADATA_VERSION = "1.0.0";
    public static final String SELECTION_POLICY_VERSION = "1.0.0";

    public enum BlueprintLevel {
        PAGE, SECTION, WIDGET, FLOW, MODAL, DRAWER, FEEDBACK, INSPECTOR
    }

    public enum BlueprintStatus {
        ACTIVE, DEPRECATED, DISABLED
    }

    public record BlueprintMetadata(
            String blueprintId,
            String version,
            BlueprintLevel level,
            BlueprintStatus status,
            Set<StageRole> supportedStages,
            Set<String> purposeTags,
            Set<String> contentTags,
            Set<String> interactionTags,
            Set<String> presentationTags,
            Set<String> requiredCapabilities,
            Set<String> requiredDataShapes,
            String runtimeVersion,
            double qualityScore,
            double stabilityScore,
            PartKind mountPoint,
            String implementationKind,
            BlueprintCategory category,
            Set<String> acceptedSurfaces,
            Set<Purpose> preferredPurposes,
            Set<String> supportedModes,
            boolean autoSelectable,
            boolean deprecated,
            String label,
            String family
    ) {
    }

    public record BlueprintSearchQuery(
            String text,
            StageRole stageRole,
            PartKind mountPoint,
            String surface,
            Purpose purpose,
            String mode,
            BlueprintCategory category,
            Set<String> availableCapabilities,
            Set<String> availableDataShapes,
            String runtimeVersion,
            RiskLevel risk,
            int limit
    ) {
        public BlueprintSearchQuery {
            text = text == null ? "" : text;
            availableCapabilities = immutable(availableCapabilities);
            availableDataShapes = immutable(availableDataShapes);
            runtimeVersion = runtimeVersion == null || runtimeVersion.isBlank() ? "3.0.0" : runtimeVersion;
            limit = Math.max(1, Math.min(limit <= 0 ? 8 : limit, 50));
        }
    }

    public record BlueprintCandidate(
            BlueprintMetadata metadata,
            double retrievalScore,
            double rankScore,
            List<String> matchedSignals
    ) {
        public BlueprintCandidate {
            matchedSignals = matchedSignals == null ? List.of() : List.copyOf(matchedSignals);
        }
    }

    public record BlueprintSearchDiagnostics(
            String engine,
            String index,
            int registryCount,
            int hardCompatibleCount,
            int retrievedCount,
            int returnedCount,
            boolean fallbackUsed,
            long tookMs,
            Map<String, Integer> rejectionCounts,
            String message,
            String metadataVersion,
            String selectionPolicyVersion
    ) {
    }

    public record BlueprintSearchResult(
            List<BlueprintCandidate> candidates,
            BlueprintSearchDiagnostics diagnostics
    ) {
        public BlueprintSearchResult {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
        }
    }

    public record BlueprintReindexResult(
            String index,
            int indexedCount,
            boolean succeeded,
            long tookMs,
            String message
    ) {
    }

    private static <T> Set<T> immutable(Set<T> values) {
        return values == null ? Set.of() : Set.copyOf(values);
    }

    private BlueprintSearchModels() {
    }
}
