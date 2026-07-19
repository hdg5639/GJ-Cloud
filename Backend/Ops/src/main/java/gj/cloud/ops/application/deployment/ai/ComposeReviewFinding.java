package gj.cloud.ops.application.deployment.ai;

public record ComposeReviewFinding(
        String code,
        ReviewSeverity severity,
        String service,
        String location,
        String message,
        String remediation,
        ReviewConfidence confidence,
        String evidence
) {
}
