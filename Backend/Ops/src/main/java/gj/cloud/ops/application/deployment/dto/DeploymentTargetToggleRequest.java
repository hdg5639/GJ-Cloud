package gj.cloud.ops.application.deployment.dto;

import jakarta.validation.constraints.NotNull;

public record DeploymentTargetToggleRequest(@NotNull Boolean enabled) {
}
