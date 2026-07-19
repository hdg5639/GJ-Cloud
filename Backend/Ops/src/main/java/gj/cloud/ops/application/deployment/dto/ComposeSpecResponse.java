package gj.cloud.ops.application.deployment.dto;

import java.util.List;

// 재시도/수정 후 재배포용 — 기존 배포에 저장된 compose 원문 및 환경변수/라우트/헬스체크 설정을 복호화해 반환.
// repoUrl/branch/patToken은 DeploymentEntity에 영속화되지 않으므로 포함하지 않음 — 재제출 시 사용자가 다시 입력해야 함.
public record ComposeSpecResponse(
        String composeContent,
        List<EnvironmentFile> environmentFiles,
        List<ExposedRoute> exposedRoutes,
        List<HealthCheck> healthChecks
) {
}
