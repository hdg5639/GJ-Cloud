package gj.cloud.ops.application.deployment.dto;

import gj.cloud.ops.domain.deployment.enums.SourceType;

import java.util.List;

// 1.3절 핵심 설계 원칙 — D-1/D-2/D-3 세 배포 방식이 이 한 지점에서 합류함.
// 이후의 Validator·Executor·상태머신은 sourceType과 무관하게 완전히 동일한 코드 경로를 탐.
public record ComposeArtifact(
        String composeContent,
        List<EnvironmentFile> environmentFiles,
        List<UploadedFile> uploadedFiles,
        List<ExposedRoute> exposedRoutes,
        List<HealthCheck> healthChecks,
        SourceType sourceType
) {
}
