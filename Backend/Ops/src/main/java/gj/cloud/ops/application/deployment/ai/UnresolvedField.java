package gj.cloud.ops.application.deployment.ai;

public record UnresolvedField(
        String field,
        String code,
        String reason
) {
}
