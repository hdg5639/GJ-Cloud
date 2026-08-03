package gj.cloud.vm.application.port.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PortDeploymentTargetLinkRequest(
        @NotBlank @Size(max = 36) String deploymentTargetId
) {
}
