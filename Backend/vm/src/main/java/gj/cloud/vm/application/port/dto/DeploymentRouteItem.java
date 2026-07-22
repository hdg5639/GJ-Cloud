package gj.cloud.vm.application.port.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Ops 서비스 ComposeArtifact.exposedRoutes 항목과 1:1 대응 (gamjabox 기획 1.5절 규칙1).
// nickname이 배포 라우트 동기화의 매칭 키로 쓰임 (vm_ports.nickname과 대조).
public record DeploymentRouteItem(
        @NotBlank String serviceName,
        @Min(1) @Max(65535) int port,
        @NotBlank String protocol,
        @NotBlank String visibility,
        @NotBlank @Size(max = 20) @Pattern(regexp = "^[a-z0-9]([a-z0-9-]*[a-z0-9])?$") String nickname,
        // PRO 전용 커스텀 서브도메인 — null/빈 값이면 기존처럼 자동 생성(PortServiceImpl.addDeploymentRoute 참고)
        @Size(max = 30) @Pattern(regexp = "^$|^[a-z0-9]([a-z0-9-]*[a-z0-9])?$")
        String customSubdomain
) {
}
