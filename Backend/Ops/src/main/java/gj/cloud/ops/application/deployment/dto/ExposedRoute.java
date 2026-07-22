package gj.cloud.ops.application.deployment.dto;

// VM 서비스 PUT /internal/ops/vms/{vmId}/deployment-routes 요청 바디와 1:1로 대응 (1.5절 규칙1)
public record ExposedRoute(
        String serviceName,
        int port,
        String protocol,
        String visibility,
        String nickname,
        // PRO 플랜 전용 커스텀 서브도메인 — null/빈 값이면 기존처럼 {vm.subdomain}-{nickname}을 자동 생성.
        // 검증(예약어/PRO 여부/중복)은 vm 서비스의 기존 PortService.validateCustomSubdomain()이 그대로 수행.
        String customSubdomain
) {
}
