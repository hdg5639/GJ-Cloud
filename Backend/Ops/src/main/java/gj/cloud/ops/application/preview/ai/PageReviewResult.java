package gj.cloud.ops.application.preview.ai;

import java.util.List;

// OpenAI structured output 대상 타입 — findings가 비어있으면(문제 없음) 그 자체가 유효한 정상 응답.
public record PageReviewResult(
        List<PageReviewFinding> findings
) {
}
