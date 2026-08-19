package gj.cloud.ops.application.deployment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;

import java.util.List;

// D-2 사용자 지정 배포 (Raw Compose) 생성 요청.
// 임의 바이너리 파일 업로드(UploadedFile)는 이 엔드포인트에서는 아직 지원하지 않음 — 후속 개선 대상.
public record DeploymentCreateRequest(
        @NotBlank String repoUrl,
        @NotBlank String branch,
        String patToken,
        @NotBlank String composeContent,
        List<EnvironmentFile> environmentFiles,
        List<@Valid ExposedRoute> exposedRoutes,
        List<HealthCheck> healthChecks,
        // 저장소 내 배포 컨텍스트 서브디렉토리 (예: "backend"). 비어있거나 "."이면 저장소 루트.
        String context,
        // VM 파일시스템 절대경로 (예: "/home/ubuntu/myapp"). 지정하면 현재 활성 release를 가리키는
        // 심볼릭 링크가 이 경로에 생성됨.
        String installPath,
        // 지속형 배포 대상. 지정하면 VM 하나 안에서도 target별 Docker/release/route가 완전히 분리된다.
        String targetName,
        Boolean autoDeploy,
        Long githubInstallationId,
        Long githubRepositoryId
) {
}
