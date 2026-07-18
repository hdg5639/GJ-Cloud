package gj.cloud.ops.application.deployment.spec;

import jakarta.validation.constraints.NotBlank;

// type: postgresql/mysql/redis/mongodb
public record InfrastructureSpec(
        @NotBlank String type,
        @NotBlank String version,
        ExposeSpec expose
) {
}
