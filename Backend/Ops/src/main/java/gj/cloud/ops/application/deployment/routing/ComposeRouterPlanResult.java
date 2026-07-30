package gj.cloud.ops.application.deployment.routing;

import java.util.List;

public record ComposeRouterPlanResult(
        String status,
        String enhancedComposeContent,
        String routerConfig,
        String routerServiceName,
        Integer routerHostPort,
        Integer routerContainerPort,
        List<ComposeRouterRoute> routes,
        List<ComposeRouterUnresolvedService> unresolvedServices,
        List<String> warnings
) {
    public static final String STATUS_ADDED = "ADDED";
    public static final String STATUS_ALREADY_CONFIGURED = "ALREADY_CONFIGURED";
    public static final String STATUS_NOT_REQUIRED = "NOT_REQUIRED";
    public static final String STATUS_NEEDS_INPUT = "NEEDS_INPUT";
}
