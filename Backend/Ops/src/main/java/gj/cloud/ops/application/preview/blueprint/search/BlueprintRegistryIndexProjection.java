package gj.cloud.ops.application.preview.blueprint.search;

import gj.cloud.ops.application.preview.blueprint.BlueprintPartRegistry;
import gj.cloud.ops.application.preview.blueprint.BlueprintPartRegistry.BlueprintPart;
import gj.cloud.ops.application.preview.blueprint.BlueprintPartRegistry.PartKind;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.StageRole;
import gj.cloud.ops.application.preview.blueprint.search.BlueprintSearchModels.BlueprintLevel;
import gj.cloud.ops.application.preview.blueprint.search.BlueprintSearchModels.BlueprintMetadata;
import gj.cloud.ops.application.preview.blueprint.search.BlueprintSearchModels.BlueprintStatus;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * component-manifest.json 정본을 검색 전용 문서로 투영한다. ES 문서는 이 결과의 파생 캐시일 뿐이며,
 * 인덱스가 유실돼도 이 함수만으로 완전히 재구축할 수 있다.
 */
public final class BlueprintRegistryIndexProjection {

    public static List<BlueprintMetadata> projectAll() {
        return BlueprintPartRegistry.ALL.stream().map(BlueprintRegistryIndexProjection::project).toList();
    }

    public static BlueprintMetadata project(BlueprintPart part) {
        Set<String> content = new LinkedHashSet<>(part.tags());
        content.add(lower(part.category().name()));
        content.add(lower(part.family()));
        Set<String> presentation = new LinkedHashSet<>(part.tags());
        presentation.add(lower(part.implementationKind()));
        presentation.add(lower(part.kind().name()));
        Set<String> interaction = interactionTags(part);
        Set<String> purpose = part.preferredPurposes().stream()
                .map(value -> lower(value.name()))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return new BlueprintMetadata(
                part.componentId(),
                BlueprintSearchModels.METADATA_VERSION,
                level(part),
                BlueprintStatus.ACTIVE,
                supportedStages(part),
                Set.copyOf(purpose),
                Set.copyOf(content),
                Set.copyOf(interaction),
                Set.copyOf(presentation),
                Set.of(),
                requiredDataShapes(part),
                ">=3.0.0",
                qualityScore(part),
                stabilityScore(part),
                part.kind(),
                part.implementationKind(),
                part.category(),
                part.acceptedSurfaces(),
                part.preferredPurposes(),
                part.supportedModes(),
                part.autoSelectable(),
                false,
                part.label(),
                part.family()
        );
    }

    private static BlueprintLevel level(BlueprintPart part) {
        return switch (part.implementationKind()) {
            case "MODAL" -> BlueprintLevel.MODAL;
            case "WORKFLOW" -> BlueprintLevel.FLOW;
            case "FEEDBACK" -> BlueprintLevel.FEEDBACK;
            case "LAYOUT", "NAVIGATION", "THEME" -> BlueprintLevel.PAGE;
            default -> switch (part.kind()) {
                case OVERLAY -> BlueprintLevel.DRAWER;
                case ACTIONS -> BlueprintLevel.WIDGET;
                default -> BlueprintLevel.SECTION;
            };
        };
    }

    private static Set<StageRole> supportedStages(BlueprintPart part) {
        return switch (part.implementationKind()) {
            case "COLLECTION" -> Set.of(StageRole.SELECT_CONTEXT, StageRole.DISCOVER,
                    StageRole.SELECT, StageRole.ACCUMULATE);
            case "DETAIL", "DASHBOARD" -> Set.of(StageRole.INSPECT, StageRole.COMPARE,
                    StageRole.VERIFY, StageRole.TRACK);
            case "FORM" -> Set.of(StageRole.AUTHENTICATE, StageRole.CONFIGURE, StageRole.PREPARE);
            case "MODAL" -> Set.of(StageRole.REVIEW, StageRole.COMMIT, StageRole.RECOVER);
            case "WORKFLOW" -> Set.of(StageRole.PREPARE, StageRole.REVIEW, StageRole.COMMIT,
                    StageRole.WAIT, StageRole.TRACK, StageRole.COMPLETE);
            case "ACTION" -> Set.of(StageRole.COMMIT, StageRole.CONTINUE);
            case "FEEDBACK" -> Set.of(StageRole.WAIT, StageRole.VERIFY, StageRole.TRACK,
                    StageRole.RECOVER, StageRole.COMPLETE);
            case "NAVIGATION" -> Set.of(StageRole.ENTRY, StageRole.SELECT_CONTEXT,
                    StageRole.DISCOVER, StageRole.CONTINUE);
            case "LAYOUT", "THEME" -> Set.of(StageRole.ENTRY);
            default -> Set.of();
        };
    }

    private static Set<String> requiredDataShapes(BlueprintPart part) {
        return switch (part.kind()) {
            case COLLECTION -> Set.of("collection");
            case DETAIL -> Set.of("record");
            case DASHBOARD -> Set.of("collection", "metrics");
            case OVERLAY -> Set.of("form-state");
            case ACTIONS -> Set.of("actions");
            default -> Set.of();
        };
    }

    private static Set<String> interactionTags(BlueprintPart part) {
        Set<String> result = new LinkedHashSet<>();
        switch (part.kind()) {
            case COLLECTION -> {
                result.add("browsable");
                result.add("selectable");
            }
            case DETAIL, DASHBOARD -> result.add("inspectable");
            case OVERLAY -> {
                result.add("editable");
                result.add("confirmable");
            }
            case ACTIONS -> result.add("actionable");
            case NAVIGATION -> result.add("navigable");
            case FEEDBACK -> result.add("observable");
            default -> result.add("presentational");
        }
        if (part.tags().contains("search") || part.tags().contains("table")) result.add("searchable");
        if (part.tags().contains("polling") || part.tags().contains("progress")) result.add("refreshable");
        return result;
    }

    private static double qualityScore(BlueprintPart part) {
        double score = 0.78;
        if (!part.tags().isEmpty()) score += 0.05;
        if (!part.preferredPurposes().isEmpty()) score += 0.04;
        if (part.acceptedSurfaces().size() > 1) score += 0.03;
        return Math.min(0.95, score);
    }

    private static double stabilityScore(BlueprintPart part) {
        return part.autoSelectable() ? 0.94 : 0.86;
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private BlueprintRegistryIndexProjection() {
    }
}
