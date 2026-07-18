package gj.cloud.vm.application.ssh.client;

import gj.cloud.vm.application.ssh.dto.ManagementKeyResponse;
import gj.cloud.vm.global.exception.VmException;
import gj.cloud.vm.global.exception.enums.VmErrorCode;
import gj.cloud.vm.global.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Slf4j
@Component
public class OpsServiceClient {

    private static final ParameterizedTypeReference<ApiResponse<ManagementKeyResponse>> MANAGEMENT_KEY_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final WebClient webClient;

    public OpsServiceClient(@Value("${ops.service-url}") String opsServiceUrl) {
        this.webClient = WebClient.builder().baseUrl(opsServiceUrl).build();
    }

    // 멱등: 같은 vmId로 재시도해도 Ops가 같은 키를 반환함 (VM 프로비저닝 재시도 대응)
    public Mono<ManagementKeyResponse> issueManagementKey(String bearerToken, UUID vmId) {
        return webClient.put()
                .uri("/internal/vms/{vmId}/management-key", vmId)
                .header("Authorization", "Bearer " + bearerToken)
                .retrieve()
                .bodyToMono(MANAGEMENT_KEY_RESPONSE_TYPE)
                .map(ApiResponse::data)
                .onErrorResume(e -> {
                    log.error("Ops 관리 키 발급 실패: vmId={}, error={}", vmId, e.getMessage());
                    return Mono.error(new VmException(VmErrorCode.OPS_KEY_ISSUE_FAILED));
                });
    }

    // VM 삭제와 강결합 금지: 실패해도 VM 삭제 흐름은 계속 진행 (Ops가 REVOKE_PENDING으로 두고 orphan cleanup)
    public Mono<Void> revokeManagementKey(String bearerToken, UUID vmId) {
        return webClient.delete()
                .uri("/internal/vms/{vmId}/management-key", vmId)
                .header("Authorization", "Bearer " + bearerToken)
                .retrieve()
                .bodyToMono(Void.class)
                .onErrorResume(e -> {
                    log.warn("Ops 관리 키 폐기 요청 실패 (무시하고 VM 삭제 계속): vmId={}, error={}", vmId, e.getMessage());
                    return Mono.empty();
                });
    }
}
