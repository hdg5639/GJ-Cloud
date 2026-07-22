package gj.cloud.vm.application.port.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record DeploymentRoutesSyncRequest(
        @NotBlank String deploymentId,
        @NotNull List<@Valid DeploymentRouteItem> routes
) {
}
