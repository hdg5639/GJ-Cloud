package gj.cloud.ops.application.deployment.spec;

import jakarta.validation.constraints.NotNull;

public record BuildSpec(
        @NotNull RuntimeKind runtime,
        String version,
        @NotNull BuildRunStrategy strategy,
        String outputPath
) {
}
