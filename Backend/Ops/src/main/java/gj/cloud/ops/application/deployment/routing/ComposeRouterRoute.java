package gj.cloud.ops.application.deployment.routing;

public record ComposeRouterRoute(
        String serviceName,
        String routePath,
        String upstream,
        int containerPort,
        boolean root
) {
}
