package gj.cloud.ops.application.deployment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DeploymentTargetConfig(@NotBlank String version, @NotNull @Valid ComposePreparationRequest config) { }
