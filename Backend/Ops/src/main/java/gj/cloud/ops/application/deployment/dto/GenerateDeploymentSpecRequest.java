package gj.cloud.ops.application.deployment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

// D-3 AI 자동생성 요청 — 서비스 카드 + 공유 인프라 선택을 AI에게 넘겨 DeploymentSpec을 생성시킴
public record GenerateDeploymentSpecRequest(
        @NotEmpty @Valid List<ServiceCard> services,
        @Valid List<InfraSelection> infrastructure
) {
}
