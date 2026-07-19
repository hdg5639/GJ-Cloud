package gj.cloud.ops.application.deployment.ai;

import gj.cloud.ops.application.deployment.spec.ServiceSpec;

import java.util.List;

// OpenAI structured output(.text(Class))의 실제 대상 타입 — AI에게는 결정론적 규칙으로 못 푼 서비스만 넘기고,
// 이 형태로만 답하게 강제한다(8절 — 프롬프트만으로 JSON을 기대하지 않고 스키마로 강제).
public record AiServiceSpecOutput(
        GenerationStatus status,
        List<ServiceSpec> services,
        List<UnresolvedField> unresolved,
        List<String> warnings
) {
}
