package gj.cloud.ops.application.preview.ai;

import gj.cloud.ops.application.deployment.ai.ReviewSeverity;

// AiComposeReviewer의 ComposeReviewFinding과 같은 역할 — 코멘트만 제공하고 Blueprint를 직접 고치지 않는다.
public record PageReviewFinding(
        String code,
        ReviewSeverity severity,
        String message,
        String remediation
) {
}
