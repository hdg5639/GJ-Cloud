package gj.cloud.ops.application.vmclient;

import gj.cloud.ops.application.vmclient.dto.VmContextResponse;
import gj.cloud.ops.application.vmclient.dto.PortDeploymentTargetLinkRequest;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import gj.cloud.ops.global.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

// VM 서비스가 VM 권한 판정의 단일 진실 공급원이므로, Ops는 이 클라이언트로만 조회하고
// VM 서비스 DB를 직접 참조하거나 조직 정보를 복제하지 않음.
@Slf4j
@Component
public class VmServiceClient {

    private static final ParameterizedTypeReference<ApiResponse<VmContextResponse>> CONTEXT_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;

    public VmServiceClient(@Value("${vm.service-url}") String vmServiceUrl) {
        this.restClient = RestClient.builder().baseUrl(vmServiceUrl).build();
    }

    // bearerToken은 요청자가 이미 갖고 있던 aud=ops-service 토큰을 그대로 포워딩 (VM 서비스가 ops-service audience로 검증)
    public VmContextResponse getContext(String bearerToken, String vmId) {
        try {
            ApiResponse<VmContextResponse> response = restClient.get()
                    .uri("/internal/ops/vms/{vmId}/context", vmId)
                    .header("Authorization", "Bearer " + bearerToken)
                    .retrieve()
                    .body(CONTEXT_RESPONSE_TYPE);
            return response.data();
        } catch (HttpClientErrorException.Forbidden e) {
            throw new OpsException(OpsErrorCode.FORBIDDEN);
        } catch (HttpClientErrorException.NotFound e) {
            throw new OpsException(OpsErrorCode.VM_NOT_FOUND);
        } catch (Exception e) {
            log.error("VM 컨텍스트 조회 실패: vmId={}, error={}", vmId, e.getMessage());
            throw new OpsException(OpsErrorCode.VM_CONTEXT_FETCH_FAILED);
        }
    }

    public void linkManualPortToDeploymentTarget(
            String bearerToken, String vmId, String portId, String deploymentTargetId
    ) {
        try {
            restClient.put()
                    .uri("/internal/ops/vms/{vmId}/ports/{portId}/deployment-target", vmId, portId)
                    .header("Authorization", "Bearer " + bearerToken)
                    .body(new PortDeploymentTargetLinkRequest(deploymentTargetId))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.Forbidden e) {
            throw new OpsException(OpsErrorCode.FORBIDDEN);
        } catch (HttpClientErrorException.NotFound e) {
            throw new OpsException(OpsErrorCode.DEPLOYMENT_CNAME_NOT_FOUND);
        } catch (Exception e) {
            log.error("수동 CNAME 배포 연결 실패: vmId={}, portId={}, targetId={}, error={}",
                    vmId, portId, deploymentTargetId, e.getMessage());
            throw new OpsException(OpsErrorCode.DEPLOYMENT_CNAME_LINK_FAILED);
        }
    }

    public void unlinkManualPortFromDeploymentTarget(
            String bearerToken, String vmId, String portId, String deploymentTargetId
    ) {
        try {
            restClient.delete()
                    .uri("/internal/ops/vms/{vmId}/ports/{portId}/deployment-target/{targetId}",
                            vmId, portId, deploymentTargetId)
                    .header("Authorization", "Bearer " + bearerToken)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.Forbidden e) {
            throw new OpsException(OpsErrorCode.FORBIDDEN);
        } catch (HttpClientErrorException.NotFound e) {
            throw new OpsException(OpsErrorCode.DEPLOYMENT_CNAME_NOT_FOUND);
        } catch (Exception e) {
            log.error("수동 CNAME 배포 연결 해제 실패: vmId={}, portId={}, targetId={}, error={}",
                    vmId, portId, deploymentTargetId, e.getMessage());
            throw new OpsException(OpsErrorCode.DEPLOYMENT_CNAME_LINK_FAILED);
        }
    }

    public void unlinkAllManualPortsFromDeploymentTarget(
            String bearerToken, String vmId, String deploymentTargetId
    ) {
        try {
            restClient.delete()
                    .uri("/internal/ops/vms/{vmId}/deployment-targets/{targetId}/manual-port-links",
                            vmId, deploymentTargetId)
                    .header("Authorization", "Bearer " + bearerToken)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("수동 CNAME 배포 연결 일괄 해제 실패: vmId={}, targetId={}, error={}",
                    vmId, deploymentTargetId, e.getMessage());
            throw new OpsException(OpsErrorCode.DEPLOYMENT_CNAME_LINK_FAILED);
        }
    }
}
