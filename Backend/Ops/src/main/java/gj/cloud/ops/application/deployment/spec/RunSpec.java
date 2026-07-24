package gj.cloud.ops.application.deployment.spec;

import jakarta.validation.constraints.NotNull;

public record RunSpec(
        @NotNull RuntimeKind runtime,
        @NotNull BuildRunStrategy strategy,
        Integer containerPort
) {
}
