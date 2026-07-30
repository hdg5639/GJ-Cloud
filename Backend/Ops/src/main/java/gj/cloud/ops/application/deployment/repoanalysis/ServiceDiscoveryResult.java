package gj.cloud.ops.application.deployment.repoanalysis;

import java.util.List;

public record ServiceDiscoveryResult(
        List<DiscoveredService> services,
        List<String> warnings
) {
}
