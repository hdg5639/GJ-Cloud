package gj.cloud.ops.application.deployment.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// VM 서비스 PUT /internal/ops/vms/{vmId}/deployment-routes 요청 바디와 1:1로 대응 (1.5절 규칙1)
public record ExposedRoute(
        @NotBlank String serviceName,
        @Min(1) @Max(65535) int port,
        @NotBlank String protocol,
        @NotBlank String visibility,
        @NotBlank @Size(max = 20) @Pattern(regexp = "^[a-z0-9]([a-z0-9-]*[a-z0-9])?$") String nickname,
        // PRO 플랜 전용 커스텀 서브도메인 — null/빈 값이면 기존처럼 {vm.subdomain}-{nickname}을 자동 생성.
        // 검증(예약어/PRO 여부/중복)은 vm 서비스의 기존 PortService.validateCustomSubdomain()이 그대로 수행.
        @Size(max = 30) @Pattern(regexp = "^$|^[a-z0-9]([a-z0-9-]*[a-z0-9])?$")
        String customSubdomain
) {
}
