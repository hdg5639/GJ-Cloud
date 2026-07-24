package gj.cloud.vm.application.ssh.client;

import gj.cloud.vm.application.ssh.dto.ManagementKeyResponse;
import gj.cloud.vm.application.ssh.dto.SshReadinessRequest;
import gj.cloud.vm.application.ssh.dto.SshReadinessResponse;
import gj.cloud.vm.global.auth.ServiceTokenClient;
import gj.cloud.vm.global.exception.VmException;
import gj.cloud.vm.global.exception.enums.VmErrorCode;
import gj.cloud.vm.global.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.UUID;

// OPS-SEC-002: 사용자가 자기 토큰을 재교환해 스스로 발급받을 수 있는 aud=vm-service 토큰을 그대로
// 전달하는 대신, VM 서비스 자신의 client-credentials로 발급받은 서비스 토큰을 사용함 —
// 사용자 신원이 아니라 "진짜 VM 서비스가 호출했다"는 것을 Ops가 검증할 수 있게 함.
@Slf4j
@Component
public class OpsServiceClient {

    private static final ParameterizedTypeReference<ApiResponse<ManagementKeyResponse>> MANAGEMENT_KEY_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiResponse<SshReadinessResponse>> SSH_READINESS_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final Duration SSH_READINESS_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration SSH_READINESS_RETRY_DELAY = Duration.ofSeconds(5);
    private static final long SSH_READINESS_MAX_RETRIES = 120;

    private final WebClient webClient;
    private final ServiceTokenClient serviceTokenClient;

    public OpsServiceClient(@Value("${ops.service-url}") String opsServiceUrl, ServiceTokenClient serviceTokenClient) {
        this.webClient = WebClient.builder().baseUrl(opsServiceUrl).build();
        this.serviceTokenClient = serviceTokenClient;
    }

    // 멱등: 같은 vmId로 재시도해도 Ops가 같은 키를 반환함 (VM 프로비저닝 재시도 대응)
    public Mono<ManagementKeyResponse> issueManagementKey(UUID vmId) {
        return serviceTokenClient.getToken()
                .flatMap(serviceToken -> webClient.put()
                        .uri("/internal/vms/{vmId}/management-key", vmId)
                        .header("Authorization", "Bearer " + serviceToken)
                        .retrieve()
                        .bodyToMono(MANAGEMENT_KEY_RESPONSE_TYPE)
                        .map(ApiResponse::data))
                .onErrorResume(e -> {
                    log.error("Ops 관리 키 발급 실패: vmId={}, error={}", vmId, e.getMessage());
                    return Mono.error(new VmException(VmErrorCode.OPS_KEY_ISSUE_FAILED));
                });
    }

    public Mono<Void> waitForSshReadiness(
            UUID vmId,
            String internalIp,
            String expectedUserPublicKey,
            String expectedUserKeyFingerprint
    ) {
        SshReadinessRequest request =
                new SshReadinessRequest(internalIp, expectedUserPublicKey, expectedUserKeyFingerprint);

        return Mono.defer(() -> probeSshReadiness(vmId, request))
                .flatMap(response -> {
                    if (response.ready()) {
                        log.info("VM SSH 준비 완료: vmId={}, ip={}", vmId, internalIp);
                        return Mono.<Void>empty();
                    }
                    if (response.terminal()) {
                        log.error("VM SSH 준비 실패: vmId={}, stage={}, detail={}",
                                vmId, response.stage(), response.detail());
                        return Mono.error(new VmException(VmErrorCode.SSH_PROVISIONING_FAILED));
                    }
                    return Mono.error(new SshReadinessPendingException(response.stage()));
                })
                .retryWhen(Retry.fixedDelay(SSH_READINESS_MAX_RETRIES, SSH_READINESS_RETRY_DELAY)
                        .filter(SshReadinessPendingException.class::isInstance)
                        .doBeforeRetry(signal -> {
                            long attempt = signal.totalRetries() + 1;
                            if (attempt == 1 || attempt % 6 == 0) {
                                SshReadinessPendingException pending =
                                        (SshReadinessPendingException) signal.failure();
                                log.info("VM SSH 준비 대기: vmId={}, stage={}, attempt={}",
                                        vmId, pending.stage(), attempt);
                            }
                        }))
                .timeout(SSH_READINESS_TIMEOUT)
                .onErrorMap(error -> {
                    if (error instanceof VmException) {
                        return error;
                    }
                    log.error("VM SSH 준비 확인 실패: vmId={}, error={}", vmId, error.getMessage());
                    return new VmException(VmErrorCode.SSH_PROVISIONING_FAILED);
                });
    }

    private Mono<SshReadinessResponse> probeSshReadiness(UUID vmId, SshReadinessRequest request) {
        return serviceTokenClient.getToken()
                .flatMap(serviceToken -> webClient.post()
                        .uri("/internal/vms/{vmId}/ssh-readiness", vmId)
                        .header("Authorization", "Bearer " + serviceToken)
                        .bodyValue(request)
                        .retrieve()
                        .bodyToMono(SSH_READINESS_RESPONSE_TYPE)
                        .map(ApiResponse::data))
                .onErrorMap(error -> new SshReadinessPendingException("OPS_UNAVAILABLE"));
    }

    // VM 삭제와 강결합 금지: 실패해도 VM 삭제 흐름은 계속 진행 (Ops가 REVOKE_PENDING으로 두고 orphan cleanup)
    public Mono<Void> revokeManagementKey(UUID vmId) {
        return serviceTokenClient.getToken()
                .flatMap(serviceToken -> webClient.delete()
                        .uri("/internal/vms/{vmId}/management-key", vmId)
                        .header("Authorization", "Bearer " + serviceToken)
                        .retrieve()
                        .bodyToMono(Void.class))
                .onErrorResume(e -> {
                    log.warn("Ops 관리 키 폐기 요청 실패 (무시하고 VM 삭제 계속): vmId={}, error={}", vmId, e.getMessage());
                    return Mono.empty();
                });
    }

    private static class SshReadinessPendingException extends RuntimeException {
        private final String stage;

        private SshReadinessPendingException(String stage) {
            super(stage);
            this.stage = stage;
        }

        private String stage() {
            return stage;
        }
    }
}
