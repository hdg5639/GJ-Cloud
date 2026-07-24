package gj.cloud.ops.application.deployment.spec;

import jakarta.validation.constraints.NotNull;

public record ArtifactSpec(
        @NotNull ArtifactType type,
        String path
) {
}
