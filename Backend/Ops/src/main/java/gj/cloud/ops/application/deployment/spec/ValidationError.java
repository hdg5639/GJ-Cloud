package gj.cloud.ops.application.deployment.spec;

// 검증 에러 하나가 두 가지 용도로 쓰여서(사용자에게 보여주는 실패 사유 / AI 재교정 프롬프트에 넣는 컨텍스트)
// 메시지를 이중화함. userMessage는 한국어로 포털에 그대로 노출되고, aiMessage는 영어로 재교정 프롬프트에
// 인코딩돼 토큰 비용을 줄인다(한국어는 GPT류 BPE 토크나이저에서 같은 의미라도 영어보다 토큰을 더 씀).
public record ValidationError(String userMessage, String aiMessage) {
}
