package gj.cloud.ops.application.preview.ai;

import java.util.List;

// aiSucceeded=false면 suggestions는 항상 빈 목록이다 — AI 호출 자체가 실패한 경우. 스왑 가능한 Block이
// 하나도 없을 때도 빈 목록을 succeeded=true로 돌려준다(제안할 대상이 없을 뿐 실패는 아님).
public record PartSuggestionResult(
        List<PartSuggestion> suggestions,
        boolean aiSucceeded
) {
}
