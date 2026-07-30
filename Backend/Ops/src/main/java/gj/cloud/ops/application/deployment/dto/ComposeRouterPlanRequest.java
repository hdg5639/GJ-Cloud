package gj.cloud.ops.application.deployment.dto;

import gj.cloud.ops.application.deployment.routing.ComposeRouterRouteOverride;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record ComposeRouterPlanRequest(
        @NotBlank String composeContent,
        @Min(1) @Max(65535) Integer routerHostPort,
        Map<String, @Min(1) @Max(65535) Integer> servicePorts,
        Map<String, @Valid ComposeRouterRouteOverride> routeOverrides
) {
}
