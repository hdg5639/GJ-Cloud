package gj.cloud.ops.application.preview.ai;

import java.util.List;

// OpenAI structured output 대상 타입 — AiPartAdvisor가 이 형태로만 응답을 받는다.
public record PartSuggestionProposal(
        List<PartSuggestion> suggestions
) {
}
