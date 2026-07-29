package gj.cloud.ops.application.preview.ai;

import gj.cloud.ops.application.preview.blueprint.composition.BlueprintCompositionModels.BlueprintCompositionFinding;

import java.util.List;
import java.util.Set;

// aiSucceeded=false면 suggestions는 항상 빈 목록이다 — AI 호출 자체가 실패한 경우. 스왑 가능한 Block이
// 하나도 없을 때도 빈 목록을 succeeded=true로 돌려준다(제안할 대상이 없을 뿐 실패는 아님).
public record PartSuggestionResult(
        List<PartSuggestion> suggestions,
        boolean aiSucceeded,
        List<BlueprintCompositionFinding> compositionFindings,
        Set<String> reselectedGroups,
        String selectionStrategy
) {
    public PartSuggestionResult(List<PartSuggestion> suggestions, boolean aiSucceeded) {
        this(suggestions, aiSucceeded, List.of(), Set.of(), null);
    }

    public PartSuggestionResult {
        suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
        compositionFindings = compositionFindings == null ? List.of() : List.copyOf(compositionFindings);
        reselectedGroups = reselectedGroups == null ? Set.of() : Set.copyOf(reselectedGroups);
    }
}
