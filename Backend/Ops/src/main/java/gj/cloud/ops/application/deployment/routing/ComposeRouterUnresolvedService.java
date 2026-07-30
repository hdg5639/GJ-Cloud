package gj.cloud.ops.application.deployment.routing;

public record ComposeRouterUnresolvedService(
        String serviceName,
        String reason,
        boolean portRequired
) {
}
