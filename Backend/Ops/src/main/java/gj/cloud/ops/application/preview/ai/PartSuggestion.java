package gj.cloud.ops.application.preview.ai;

// 스왑 가능한 Block 하나에 대한 파츠 추천. componentId는 그 Block의 kind/slot과 호환되는 등록 파츠 id
// (또는 현재 기본 컴포넌트 id = "기본 유지")여야 하며, AiPartAdvisor가 결정론적으로 다시 검증한다.
// OpenAI structured output 대상 타입이자, 검증 통과분을 담는 응답 원소로도 그대로 쓴다.
public record PartSuggestion(
        String pageId,
        String instanceId,
        String componentId,
        String reason
) {
}
