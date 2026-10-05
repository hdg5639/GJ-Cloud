package gj.cloud.ops.application.deployment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record ComposePreparationRequest(@NotBlank String composeContent, List<EnvironmentFile> environmentFiles,
        List<@Valid ExposedRoute> exposedRoutes, List<HealthCheck> healthChecks, String context, String caddyfileOverride) {
    public ComposePreparationRequest(String composeContent, List<EnvironmentFile> environmentFiles,
            List<ExposedRoute> exposedRoutes, List<HealthCheck> healthChecks, String context) {
        this(composeContent, environmentFiles, exposedRoutes, healthChecks, context, null);
    }
}
