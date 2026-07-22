package gj.cloud.ops.application.deployment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// D-3 서비스 카드 — 사용자가 입력하는 경량 스택 정보. 나머지(healthCheckPath 등)는 AI가 DeploymentSpec으로 완성함.
public record ServiceCard(
        @NotBlank String name,
        @NotBlank String runtime,
        @NotBlank String context,
        @NotNull Integer containerPort,
        Integer javaVersion,
        String buildTool,
        Integer nodeVersion,
        String buildCommand,
        String startCommand,
        String pythonVersion,
        String pythonFramework,
        boolean expose,
        @Size(max = 30)
        @Pattern(regexp = "^$|^[a-z0-9]([a-z0-9-]*[a-z0-9])?$")
        String customSubdomain
) {
}
