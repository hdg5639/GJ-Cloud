package gj.cloud.ops.application.deployment.spec;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

// D-1(기본 템플릿)/D-3(AI 자동생성)이 공유하는 스펙 구조 — 둘 다 같은 Renderer로 ComposeArtifact를 만듦 (F절 7·9단계).
// schemaVersion을 유지해 향후 필드 변경 시 과거 배포 기록과의 호환을 관리 (I절 체크리스트).
public record DeploymentSpec(
        @NotBlank String schemaVersion,
        @NotEmpty @Valid List<ServiceSpec> services,
        @Valid List<InfrastructureSpec> infrastructure,
        @NotBlank String network,
        // true면 network가 VM에 이미 존재하는 Docker 네트워크 이름 — 새로 만들지 않고 external로 재사용.
        boolean externalNetwork
) {
}
