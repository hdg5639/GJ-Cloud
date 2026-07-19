package gj.cloud.ops.application.deployment.ai;

import java.util.List;

// OpenAI structured output 대상 타입 — findings가 없으면(문제 없음) 빈 리스트가 유효한 정상 응답이다.
public record ComposeReviewResult(
        List<ComposeReviewFinding> findings
) {
}
