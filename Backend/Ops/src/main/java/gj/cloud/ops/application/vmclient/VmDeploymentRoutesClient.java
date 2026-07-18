package gj.cloud.ops.application.vmclient;

import gj.cloud.ops.application.deployment.dto.DeploymentRoutesRequest;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

// 1.5절 규칙1 — Cloudflare/포트 소유권은 VM 서비스에 유지. Ops는 exposedRoutes만 넘기고
// 실제 Cloudflare 조작·중복검사·포트 제한은 VM 서비스의 기존 PortService가 담당함.
// 주의: 이 클라이언트가 호출하는 VM 서비스 측 PUT /internal/ops/vms/{vmId}/deployment-routes 엔드포인트는
// 아직 구현되지 않았음 (audience 검증 체인만 준비된 상태) — VM 서비스 쪽 구현이 완료돼야 ROUTING 단계가 실제로 동작함.
@Slf4j
@Component
public class VmDeploymentRoutesClient {

    private final RestClient restClient;

    public VmDeploymentRoutesClient(@Value("${vm.service-url}") String vmServiceUrl) {
        this.restClient = RestClient.builder().baseUrl(vmServiceUrl).build();
    }

    public void syncRoutes(String bearerToken, String vmId, DeploymentRoutesRequest request) {
        try {
            restClient.put()
                    .uri("/internal/ops/vms/{vmId}/deployment-routes", vmId)
                    .header("Authorization", "Bearer " + bearerToken)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("배포 라우트 동기화 실패: vmId={}, error={}", vmId, e.getMessage());
            throw new OpsException(OpsErrorCode.VM_CONTEXT_FETCH_FAILED);
        }
    }
}
