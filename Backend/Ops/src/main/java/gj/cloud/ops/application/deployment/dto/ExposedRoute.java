package gj.cloud.ops.application.deployment.dto;

// VM 서비스 PUT /internal/ops/vms/{vmId}/deployment-routes 요청 바디와 1:1로 대응 (1.5절 규칙1)
public record ExposedRoute(
        String serviceName,
        int port,
        String protocol,
        String visibility,
        String nickname
) {
}
