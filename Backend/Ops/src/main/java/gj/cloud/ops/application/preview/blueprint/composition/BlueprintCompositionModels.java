package gj.cloud.ops.application.preview.blueprint.composition;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BlueprintCompositionModels {

    public enum SelectionMode {
        PICK_ONE,
        OPTIONAL_ONE,
        PICK_MANY,
        ORDER_MANY
    }

    public enum FindingSeverity {
        WARNING,
        ERROR
    }

    public record BlueprintCandidateOption(
            String componentId,
            String family,
            String implementationKind,
            String overlayPresentation,
            double retrievalRank,
            boolean baseComponent
    ) {
    }

    public record BlueprintExclusiveGroup(
            String id,
            String pageId,
            String instanceId,
            String slot,
            SelectionMode selectionMode,
            String currentComponentId,
            List<BlueprintCandidateOption> candidates
    ) {
        public BlueprintExclusiveGroup {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
        }
    }

    public record BlueprintSelection(
            String groupId,
            String componentId,
            String source
    ) {
    }

    public record BlueprintCompositionFinding(
            FindingSeverity severity,
            String code,
            String message,
            List<String> groupIds,
            List<String> reselectableGroupIds
    ) {
        public BlueprintCompositionFinding {
            groupIds = groupIds == null ? List.of() : List.copyOf(groupIds);
            reselectableGroupIds = reselectableGroupIds == null ? List.of() : List.copyOf(reselectableGroupIds);
        }
    }

    public record BlueprintCompositionResult(
            List<BlueprintSelection> selections,
            List<BlueprintCompositionFinding> findings,
            Set<String> reselectedGroupIds,
            boolean valid,
            String strategy
    ) {
        public BlueprintCompositionResult {
            selections = selections == null ? List.of() : List.copyOf(selections);
            findings = findings == null ? List.of() : List.copyOf(findings);
            reselectedGroupIds = reselectedGroupIds == null ? Set.of() : Set.copyOf(reselectedGroupIds);
        }

        public Map<String, String> selectionByGroup() {
            return selections.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                    BlueprintSelection::groupId,
                    BlueprintSelection::componentId,
                    (left, right) -> left
            ));
        }
    }

    private BlueprintCompositionModels() {
    }
}
