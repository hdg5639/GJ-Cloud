package gj.cloud.ops.application.vmclient;

import gj.cloud.ops.application.deployment.dto.DeploymentRoutesRequest;
import gj.cloud.ops.application.vmclient.dto.AutomationRoutesRequest;
import gj.cloud.ops.application.vmclient.dto.VmContextResponse;
import gj.cloud.ops.global.auth.ServiceTokenClient;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import gj.cloud.ops.global.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class VmAutomationClient {

    private static final ParameterizedTypeReference<ApiResponse<VmContextResponse>> CONTEXT_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final ServiceTokenClient serviceTokenClient;

    public VmAutomationClient(
            @Value("${vm.service-url}") String vmServiceUrl,
            ServiceTokenClient serviceTokenClient,
            RestClient.Builder restClientBuilder
    ) {
        this.restClient = restClientBuilder.baseUrl(vmServiceUrl).build();
        this.serviceTokenClient = serviceTokenClient;
    }

    public VmContextResponse getContext(String vmId, String ownerUserId, String ownerEmail) {
        try {
            ApiResponse<VmContextResponse> response = restClient.get()
                    .uri(uri -> uri.path("/internal/automation/vms/{vmId}/context")
                            .queryParam("ownerUserId", ownerUserId)
                            .queryParam("ownerEmail", ownerEmail)
                            .build(vmId))
                    .header("Authorization", "Bearer " + serviceTokenClient.getToken())
                    .retrieve()
                    .body(CONTEXT_RESPONSE_TYPE);
            return response.data();
        } catch (HttpClientErrorException.NotFound e) {
            throw new OpsException(OpsErrorCode.VM_NOT_FOUND);
        } catch (HttpClientErrorException.Forbidden e) {
            throw new OpsException(OpsErrorCode.FORBIDDEN);
        } catch (Exception e) {
            log.error("자동 배포 VM 컨텍스트 조회 실패: vmId={}, error={}", vmId, e.getMessage());
            throw new OpsException(OpsErrorCode.VM_CONTEXT_FETCH_FAILED);
        }
    }

    public void syncRoutes(
            String vmId,
            String ownerUserId,
            String ownerEmail,
            String deploymentAppId,
            DeploymentRoutesRequest routes
    ) {
        try {
            restClient.put()
                    .uri("/internal/automation/vms/{vmId}/deployment-routes", vmId)
                    .header("Authorization", "Bearer " + serviceTokenClient.getToken())
                    .body(new AutomationRoutesRequest(ownerUserId, ownerEmail, routes))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("자동 배포 라우트 동기화 실패: vmId={}, targetId={}, error={}",
                    vmId, deploymentAppId, e.getMessage());
            throw new OpsException(OpsErrorCode.VM_CONTEXT_FETCH_FAILED);
        }
    }
}
