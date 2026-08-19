package gj.cloud.ops.application.vmclient;

import gj.cloud.ops.application.deployment.dto.DeploymentRoutesRequest;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

// 1.5절 규칙1 — Cloudflare/포트 소유권은 VM 서비스에 유지. Ops는 exposedRoutes만 넘기고
// 실제 Cloudflare 조작·중복검사·포트 제한은 VM 서비스의 기존 PortService가 담당함.
// VM 서비스 측 PUT /internal/ops/vms/{vmId}/deployment-routes는 InternalOpsController.syncDeploymentRoutes로
// 구현되어 있음 — 배포마다 원하는 라우트 집합으로 동기화(diff)하며, "내리기"에서 포트를 선택 삭제할 때도
// 이 동일한 엔드포인트를 재사용한다(원하는 집합에서 빼면 됨, DeploymentExecutor.runTeardown 참고).
@Slf4j
@Component
public class VmDeploymentRoutesClient {

    private final RestClient restClient;

    public VmDeploymentRoutesClient(@Value("${vm.service-url}") String vmServiceUrl) {
        this(vmServiceUrl, RestClient.builder());
    }

    VmDeploymentRoutesClient(String vmServiceUrl, RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.baseUrl(vmServiceUrl).build();
    }

    public void syncRoutes(String bearerToken, String vmId, DeploymentRoutesRequest request) {
        try {
            restClient.put()
                    .uri("/internal/ops/vms/{vmId}/deployment-routes", vmId)
                    .header("Authorization", "Bearer " + bearerToken)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            OpsException mapped = VmClientErrorMapper.routeSync(e);
            log.error("배포 라우트 동기화 거부: vmId={}, status={}, error={}",
                    vmId, e.getStatusCode().value(), mapped.getMessage());
            throw mapped;
        } catch (Exception e) {
            log.error("배포 라우트 동기화 실패: vmId={}, error={}", vmId, e.getMessage());
            throw new OpsException(OpsErrorCode.DEPLOYMENT_ROUTE_SYNC_FAILED);
        }
    }
}
