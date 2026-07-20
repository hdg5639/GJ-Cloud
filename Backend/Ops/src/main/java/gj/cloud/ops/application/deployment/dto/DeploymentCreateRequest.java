package gj.cloud.ops.application.deployment.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

// D-2 사용자 지정 배포 (Raw Compose) 생성 요청.
// 임의 바이너리 파일 업로드(UploadedFile)는 이 엔드포인트에서는 아직 지원하지 않음 — 후속 개선 대상.
public record DeploymentCreateRequest(
        @NotBlank String repoUrl,
        @NotBlank String branch,
        String patToken,
        @NotBlank String composeContent,
        List<EnvironmentFile> environmentFiles,
        List<ExposedRoute> exposedRoutes,
        List<HealthCheck> healthChecks,
        // 저장소 내 배포 컨텍스트 서브디렉토리 (예: "backend"). 비어있거나 "."이면 저장소 루트.
        String context
) {
}
