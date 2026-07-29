package gj.cloud.ops.application.preview.blueprint.search;

import gj.cloud.ops.application.preview.analysis.RiskLevel;
import gj.cloud.ops.application.preview.blueprint.search.BlueprintSearchModels.BlueprintMetadata;
import gj.cloud.ops.application.preview.blueprint.search.BlueprintSearchModels.BlueprintSearchQuery;
import gj.cloud.ops.application.preview.blueprint.search.BlueprintSearchModels.BlueprintStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BlueprintCompatibilityFilter {

    public record FilterResult(List<BlueprintMetadata> compatible, Map<String, Integer> rejectionCounts) {
    }

    public static FilterResult filter(List<BlueprintMetadata> documents, BlueprintSearchQuery query) {
        List<BlueprintMetadata> compatible = new ArrayList<>();
        Map<String, Integer> rejected = new LinkedHashMap<>();
        for (BlueprintMetadata document : documents) {
            String reason = rejectionReason(document, query);
            if (reason == null) compatible.add(document);
            else rejected.merge(reason, 1, Integer::sum);
        }
        return new FilterResult(List.copyOf(compatible), Map.copyOf(rejected));
    }

    static String rejectionReason(BlueprintMetadata document, BlueprintSearchQuery query) {
        if (document.status() != BlueprintStatus.ACTIVE || document.deprecated()) return "inactive_or_deprecated";
        if (!document.autoSelectable()) return "not_auto_selectable";
        if (query.mountPoint() != null && document.mountPoint() != query.mountPoint()) return "mount_point";
        if (query.surface() != null && !query.surface().isBlank()
                && !document.acceptedSurfaces().contains(query.surface())) return "surface";
        if (query.purpose() != null && !document.preferredPurposes().isEmpty()
                && !document.preferredPurposes().contains(query.purpose())) return "purpose";
        if (query.mode() != null && !query.mode().isBlank() && !document.supportedModes().isEmpty()
                && !document.supportedModes().contains(query.mode())) return "mode";
        if (query.stageRole() != null && !document.supportedStages().contains(query.stageRole())) return "stage";
        if (!query.availableCapabilities().containsAll(document.requiredCapabilities())) return "capability";
        if (!query.availableDataShapes().containsAll(document.requiredDataShapes())) return "data_shape";
        if (!runtimeCompatible(document.runtimeVersion(), query.runtimeVersion())) return "runtime";
        if (highRisk(query.risk()) && document.mountPoint().name().equals("OVERLAY")
                && document.presentationTags().stream().noneMatch(BlueprintCompatibilityFilter::isSafetyTag)) {
            return "risk_policy";
        }
        return null;
    }

    private static boolean runtimeCompatible(String requirement, String runtimeVersion) {
        if (requirement == null || requirement.isBlank()) return true;
        int requiredMajor = major(requirement.replace(">=", ""));
        int actualMajor = major(runtimeVersion);
        return requiredMajor <= 0 || actualMajor >= requiredMajor;
    }

    private static int major(String value) {
        try {
            String normalized = value == null ? "" : value.trim();
            return Integer.parseInt(normalized.split("\\.")[0]);
        } catch (RuntimeException ignored) {
            return -1;
        }
    }

    private static boolean highRisk(RiskLevel risk) {
        return risk == RiskLevel.DESTRUCTIVE || risk == RiskLevel.IRREVERSIBLE;
    }

    private static boolean isSafetyTag(String tag) {
        return tag.contains("danger") || tag.contains("confirm") || tag.contains("impact")
                || tag.contains("approval") || tag.contains("destructive");
    }

    private BlueprintCompatibilityFilter() {
    }
}
