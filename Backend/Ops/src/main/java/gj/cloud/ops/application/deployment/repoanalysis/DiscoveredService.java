package gj.cloud.ops.application.deployment.repoanalysis;

import java.util.List;

public record DiscoveredService(
        String name,
        String context,
        String runtime,
        Integer containerPort,
        boolean expose,
        String confidence,
        List<String> evidence
) {
}
