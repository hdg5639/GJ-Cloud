package gj.cloud.ops.application.deployment.spec;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// runtime별로 실제 쓰이는 필드가 다름 (D-3 예시와 동일한 평면 구조):
//   spring-boot   → javaVersion, buildTool(gradle/maven)
//   nextjs        → nodeVersion, buildCommand
//   react-nginx   → nodeVersion, buildCommand
//   nodejs        → nodeVersion, startCommand
//   nestjs        → nodeVersion
//   python        → pythonVersion, pythonFramework(fastapi/django/flask)
public record ServiceSpec(
        @NotBlank String name,
        @NotBlank String runtime,
        Integer javaVersion,
        String buildTool,
        Integer nodeVersion,
        String buildCommand,
        String startCommand,
        String pythonVersion,
        String pythonFramework,
        @NotBlank String context,
        @NotNull Integer containerPort,
        ExposeSpec expose
) {
}
