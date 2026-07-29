package gj.cloud.ops.application.preview.blueprint.composition;

import gj.cloud.ops.application.preview.blueprint.composition.BlueprintCompositionModels.BlueprintCandidateOption;
import gj.cloud.ops.application.preview.blueprint.composition.BlueprintCompositionModels.BlueprintExclusiveGroup;
import gj.cloud.ops.application.preview.blueprint.composition.BlueprintCompositionModels.BlueprintSelection;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class DiversityAwareSelectionStrategy implements BlueprintSelectionStrategy {

    private final BlueprintUsageTracker usageTracker;

    public DiversityAwareSelectionStrategy(BlueprintUsageTracker usageTracker) {
        this.usageTracker = usageTracker;
    }

    @Override
    public String name() {
        return "diversity-aware-v1";
    }

    @Override
    public List<BlueprintSelection> select(
            List<BlueprintExclusiveGroup> groups,
            Map<String, String> preferredComponentByGroup
    ) {
        Map<String, Integer> localComponentUse = new HashMap<>();
        Map<String, Integer> localFamilyUse = new HashMap<>();
        List<BlueprintSelection> selections = new ArrayList<>();
        for (BlueprintExclusiveGroup group : groups) {
            String preferred = preferredComponentByGroup.get(group.id());
            BlueprintCandidateOption selected = group.candidates().stream()
                    .filter(candidate -> candidate.componentId().equals(preferred))
                    .findFirst()
                    .orElseGet(() -> group.candidates().stream()
                            .max(java.util.Comparator.comparingDouble(candidate ->
                                    score(candidate, localComponentUse, localFamilyUse)))
                            .orElse(null));
            if (selected == null) continue;
            selections.add(new BlueprintSelection(
                    group.id(),
                    selected.componentId(),
                    selected.componentId().equals(preferred) ? "PREFERRED" : "DIVERSITY_POLICY"
            ));
            localComponentUse.merge(selected.componentId(), 1, Integer::sum);
            localFamilyUse.merge(selected.family(), 1, Integer::sum);
        }
        return List.copyOf(selections);
    }

    double score(
            BlueprintCandidateOption candidate,
            Map<String, Integer> localComponentUse,
            Map<String, Integer> localFamilyUse
    ) {
        double score = candidate.retrievalRank();
        score -= localComponentUse.getOrDefault(candidate.componentId(), 0) * 0.50;
        score -= localFamilyUse.getOrDefault(candidate.family(), 0) * 0.18;
        score -= Math.log1p(usageTracker.frequency(candidate.componentId())) * 0.025;
        if (candidate.baseComponent()) score -= 0.04;
        return score;
    }
}
