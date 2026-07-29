package gj.cloud.ops.application.preview.blueprint.composition;

import gj.cloud.ops.application.preview.blueprint.composition.BlueprintCompositionModels.BlueprintCandidateOption;
import gj.cloud.ops.application.preview.blueprint.composition.BlueprintCompositionModels.BlueprintCompositionFinding;
import gj.cloud.ops.application.preview.blueprint.composition.BlueprintCompositionModels.BlueprintCompositionResult;
import gj.cloud.ops.application.preview.blueprint.composition.BlueprintCompositionModels.BlueprintExclusiveGroup;
import gj.cloud.ops.application.preview.blueprint.composition.BlueprintCompositionModels.BlueprintSelection;
import gj.cloud.ops.application.preview.blueprint.composition.BlueprintCompositionModels.FindingSeverity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class BlueprintCompositionService {

    private static final int MAX_RESELECTION_PASSES = 3;

    private final BlueprintSelectionStrategy selectionStrategy;
    private final BlueprintUsageTracker usageTracker;

    public BlueprintCompositionService(
            BlueprintSelectionStrategy selectionStrategy,
            BlueprintUsageTracker usageTracker
    ) {
        this.selectionStrategy = selectionStrategy;
        this.usageTracker = usageTracker;
    }

    public BlueprintCompositionResult compose(
            List<BlueprintExclusiveGroup> groups,
            Map<String, String> preferredComponentByGroup
    ) {
        List<BlueprintExclusiveGroup> safeGroups = groups == null ? List.of() : List.copyOf(groups);
        Map<String, BlueprintExclusiveGroup> groupById = safeGroups.stream()
                .collect(java.util.stream.Collectors.toMap(
                        BlueprintExclusiveGroup::id, value -> value,
                        (left, right) -> left, LinkedHashMap::new));
        Map<String, BlueprintSelection> selected = selectionStrategy
                .select(safeGroups, preferredComponentByGroup == null ? Map.of() : preferredComponentByGroup)
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        BlueprintSelection::groupId, value -> value,
                        (left, right) -> left, LinkedHashMap::new));
        Set<String> reselected = new LinkedHashSet<>();

        for (int pass = 0; pass < MAX_RESELECTION_PASSES; pass++) {
            List<BlueprintCompositionFinding> findings =
                    BlueprintCompositionValidator.validate(safeGroups, List.copyOf(selected.values()));
            List<String> conflicts = findings.stream()
                    .flatMap(finding -> finding.reselectableGroupIds().stream())
                    .distinct()
                    .toList();
            if (conflicts.isEmpty()) break;
            boolean changed = false;
            int baselinePenalty = penalty(findings);
            for (String groupId : conflicts) {
                BlueprintExclusiveGroup group = groupById.get(groupId);
                BlueprintSelection current = selected.get(groupId);
                if (group == null || current == null) continue;
                Alternative alternative = bestAlternative(
                        safeGroups, selected, group, current.componentId(), baselinePenalty);
                if (alternative != null && alternative.penalty() < baselinePenalty) {
                    selected.put(groupId, new BlueprintSelection(
                            groupId, alternative.option().componentId(), "LOCAL_RESELECT"));
                    reselected.add(groupId);
                    baselinePenalty = alternative.penalty();
                    changed = true;
                }
            }
            if (!changed) break;
        }

        List<BlueprintSelection> selections = List.copyOf(selected.values());
        List<BlueprintCompositionFinding> finalFindings =
                BlueprintCompositionValidator.validate(safeGroups, selections);
        selections.forEach(selection -> usageTracker.record(selection.componentId()));
        boolean valid = finalFindings.stream().noneMatch(finding -> finding.severity() == FindingSeverity.ERROR);
        return new BlueprintCompositionResult(
                selections, finalFindings, reselected, valid, selectionStrategy.name());
    }

    private Alternative bestAlternative(
            List<BlueprintExclusiveGroup> groups,
            Map<String, BlueprintSelection> selected,
            BlueprintExclusiveGroup group,
            String currentComponentId,
            int baselinePenalty
    ) {
        List<Alternative> alternatives = new ArrayList<>();
        for (BlueprintCandidateOption candidate : group.candidates()) {
            if (candidate.componentId().equals(currentComponentId)) continue;
            Map<String, BlueprintSelection> trial = new LinkedHashMap<>(selected);
            trial.put(group.id(), new BlueprintSelection(group.id(), candidate.componentId(), "LOCAL_RESELECT"));
            int value = penalty(BlueprintCompositionValidator.validate(groups, List.copyOf(trial.values())));
            if (value <= baselinePenalty) alternatives.add(new Alternative(candidate, value));
        }
        return alternatives.stream()
                .min(Comparator.comparingInt(Alternative::penalty)
                        .thenComparing(Comparator.comparingDouble(
                                (Alternative value) -> value.option().retrievalRank()).reversed())
                        .thenComparing(value -> value.option().componentId()))
                .orElse(null);
    }

    private int penalty(List<BlueprintCompositionFinding> findings) {
        return findings.stream().mapToInt(finding ->
                (finding.severity() == FindingSeverity.ERROR ? 100 : 10)
                        + finding.reselectableGroupIds().size()).sum();
    }

    private record Alternative(BlueprintCandidateOption option, int penalty) {
    }
}
