package gj.cloud.ops.application.deployment.dto;

import java.util.List;

// VM 서비스 PUT /internal/ops/vms/{vmId}/deployment-routes 요청 바디 (1.5절 규칙1)
public record DeploymentRoutesRequest(String deploymentAppId, String deploymentId, List<ExposedRoute> routes) {
}
