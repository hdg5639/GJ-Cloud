package gj.cloud.vm.application.port.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AutomationDeploymentRoutesRequest(
        @NotBlank String ownerUserId,
        @NotBlank String ownerEmail,
        @NotNull @Valid DeploymentRoutesSyncRequest routes
) {
}
