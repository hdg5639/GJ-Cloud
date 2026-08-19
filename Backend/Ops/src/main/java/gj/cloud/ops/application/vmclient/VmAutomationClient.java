package gj.cloud.ops.application.vmclient;

import gj.cloud.ops.application.deployment.dto.DeploymentRoutesRequest;
import gj.cloud.ops.application.vmclient.dto.AutomationRoutesRequest;
import gj.cloud.ops.application.vmclient.dto.VmContextResponse;
import gj.cloud.ops.application.vmclient.dto.VmExistenceRequest;
import gj.cloud.ops.application.vmclient.dto.VmExistenceResponse;
import gj.cloud.ops.global.auth.ServiceTokenClient;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import gj.cloud.ops.global.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.Set;

@Slf4j
@Component
public class VmAutomationClient {

    private static final ParameterizedTypeReference<ApiResponse<VmContextResponse>> CONTEXT_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiResponse<VmExistenceResponse>> EXISTENCE_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final ServiceTokenClient serviceTokenClient;

    @Autowired
    public VmAutomationClient(
            @Value("${vm.service-url}") String vmServiceUrl,
            ServiceTokenClient serviceTokenClient
    ) {
        this(vmServiceUrl, serviceTokenClient, RestClient.builder());
    }

    VmAutomationClient(
            String vmServiceUrl,
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

    public Set<String> findExistingVmIds(Set<String> vmIds) {
        try {
            ApiResponse<VmExistenceResponse> response = restClient.post()
                    .uri("/internal/automation/vms/existence")
                    .header("Authorization", "Bearer " + serviceTokenClient.getToken())
                    .body(new VmExistenceRequest(new ArrayList<>(vmIds)))
                    .retrieve()
                    .body(EXISTENCE_RESPONSE_TYPE);
            if (response == null || response.data() == null || response.data().existingVmIds() == null) {
                throw new IllegalStateException("VM 존재 여부 응답이 비어 있습니다.");
            }
            return Set.copyOf(response.data().existingVmIds());
        } catch (Exception e) {
            log.error("VM 존재 여부 일괄 조회 실패: count={}, error={}", vmIds.size(), e.getMessage());
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
        } catch (RestClientResponseException e) {
            OpsException mapped = VmClientErrorMapper.routeSync(e);
            log.error("자동 배포 라우트 동기화 거부: vmId={}, targetId={}, status={}, error={}",
                    vmId, deploymentAppId, e.getStatusCode().value(), mapped.getMessage());
            throw mapped;
        } catch (Exception e) {
            log.error("자동 배포 라우트 동기화 실패: vmId={}, targetId={}, error={}",
                    vmId, deploymentAppId, e.getMessage());
            throw new OpsException(OpsErrorCode.DEPLOYMENT_ROUTE_SYNC_FAILED);
        }
    }
}
