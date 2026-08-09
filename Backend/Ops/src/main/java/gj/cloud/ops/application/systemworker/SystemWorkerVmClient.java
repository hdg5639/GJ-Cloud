package gj.cloud.ops.application.systemworker;

import gj.cloud.ops.application.systemworker.dto.SystemWorkerVmResponse;
import gj.cloud.ops.application.systemworker.dto.ManagedPreviewRouteResponse;
import gj.cloud.ops.global.auth.ServiceTokenClient;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import gj.cloud.ops.global.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Component
public class SystemWorkerVmClient {
    private static final ParameterizedTypeReference<ApiResponse<SystemWorkerVmResponse>> TYPE = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiResponse<ManagedPreviewRouteResponse>> ROUTE_TYPE = new ParameterizedTypeReference<>() {};
    private final RestClient client;
    private final ServiceTokenClient tokenClient;

    public SystemWorkerVmClient(@Value("${vm.service-url}") String url, ServiceTokenClient tokenClient) {
        this.client = RestClient.builder().baseUrl(url).build();
        this.tokenClient = tokenClient;
    }

    public SystemWorkerVmResponse provision(SystemWorkerProperties p, String publicKey) {
        return post("", Map.of("role", "AUTO_PREVIEW", "vmId", p.getPreferredVmid(), "cores", p.getCores(),
                "memoryMb", p.getMemoryMb(), "diskGb", p.getDiskGb(), "templateVmid", p.getTemplateVmid(),
                "sshPublicKey", publicKey));
    }
    public SystemWorkerVmResponse status(int vmId) {
        try {
            ApiResponse<SystemWorkerVmResponse> response = client.get().uri(base() + "/{vmId}", vmId)
                    .header("Authorization", bearer()).retrieve().body(TYPE);
            return requireData(response);
        } catch (Exception e) { throw failed(e); }
    }
    public SystemWorkerVmResponse action(int vmId, String action) { return post("/" + vmId + "/" + action, null); }

    public ManagedPreviewRouteResponse createRoute(int vmId, String subdomain, int port) {
        try {
            ApiResponse<ManagedPreviewRouteResponse> response = client.post()
                    .uri(base() + "/{vmId}/preview-routes", vmId).header("Authorization", bearer())
                    .body(Map.of("subdomain", subdomain, "port", port)).retrieve().body(ROUTE_TYPE);
            if (response == null || response.data() == null) throw new OpsException(OpsErrorCode.SYSTEM_WORKER_VM_FAILED);
            return response.data();
        } catch (Exception e) { throw failed(e); }
    }

    public void deleteRoute(int vmId, String subdomain, String dnsRecordId) {
        if (dnsRecordId == null) return;
        try {
            client.method(org.springframework.http.HttpMethod.DELETE)
                    .uri(base() + "/{vmId}/preview-routes", vmId).header("Authorization", bearer())
                    .body(Map.of("subdomain", subdomain, "dnsRecordId", dnsRecordId)).retrieve().toBodilessEntity();
        } catch (Exception e) {
            log.error("관리형 Preview 라우트 정리 실패: {}", e.getMessage());
            throw new OpsException(OpsErrorCode.SYSTEM_WORKER_VM_FAILED);
        }
    }

    private SystemWorkerVmResponse post(String suffix, Object body) {
        try {
            RestClient.RequestBodySpec request = client.post().uri(base() + suffix).header("Authorization", bearer());
            if (body != null) request.body(body);
            return requireData(request.retrieve().body(TYPE));
        } catch (Exception e) { throw failed(e); }
    }
    private String base() { return "/internal/automation/system-workers/auto-preview"; }
    private String bearer() { return "Bearer " + tokenClient.getToken(); }
    private SystemWorkerVmResponse requireData(ApiResponse<SystemWorkerVmResponse> response) {
        if (response == null || response.data() == null) throw new OpsException(OpsErrorCode.SYSTEM_WORKER_VM_FAILED);
        return response.data();
    }
    private OpsException failed(Exception e) {
        log.error("시스템 워커 VM 호출 실패: {}", e.getMessage());
        return new OpsException(OpsErrorCode.SYSTEM_WORKER_VM_FAILED);
    }
}
