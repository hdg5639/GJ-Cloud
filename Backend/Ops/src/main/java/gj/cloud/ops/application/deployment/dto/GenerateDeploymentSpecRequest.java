package gj.cloud.ops.application.deployment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

// D-3 AI 자동생성 요청 — 서비스 카드 + 공유 인프라 선택을 AI에게 넘겨 DeploymentSpec을 생성시킴.
// repoUrl/branch/patToken 추가(AI-Deployment-Pipeline.md 3절): 생성 전에 결정론적 저장소 분석을 하려면
// 이 시점에 실제 저장소 접근 정보가 필요함 — 이전에는 "배포 제출" 시점에만 받았던 값을 생성 시점으로 앞당김.
public record GenerateDeploymentSpecRequest(
        @NotBlank String repoUrl,
        @NotBlank String branch,
        String patToken,
        @NotEmpty @Valid List<ServiceCard> services,
        @Valid List<InfraSelection> infrastructure,
        // 지정하면 새 네트워크를 만드는 대신 VM에 이미 존재하는 이 이름의 Docker 네트워크를 external로 재사용.
        // null/빈 값이면 기존과 동일하게 새 네트워크를 생성.
        String existingNetworkName,
        Long githubInstallationId,
        Long githubRepositoryId
) {
}
