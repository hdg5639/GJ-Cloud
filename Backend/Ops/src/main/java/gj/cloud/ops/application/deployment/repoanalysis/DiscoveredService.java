package gj.cloud.ops.application.deployment.repoanalysis;

import java.util.List;

public record DiscoveredService(
        String name,
        String context,
        String runtime,
        Integer containerPort,
        String portSource,
        boolean expose,
        String confidence,
        List<String> evidence
) {
    public static final String PORT_SOURCE_DOCKERFILE_EXPOSE = "DOCKERFILE_EXPOSE";
    public static final String PORT_SOURCE_APPLICATION_CONFIG = "APPLICATION_CONFIG";
    public static final String PORT_SOURCE_DEFAULT = "DEFAULT";
}
