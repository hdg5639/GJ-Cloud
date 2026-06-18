package gj.cloud.vm.application.ssh.client;

import gj.cloud.vm.application.ssh.dto.SshKeyInternalResponse;
import gj.cloud.vm.global.exception.VmException;
import gj.cloud.vm.global.exception.enums.VmErrorCode;
import gj.cloud.vm.global.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class UserServiceClient {

    private static final ParameterizedTypeReference<ApiResponse<SshKeyInternalResponse>> SSH_KEY_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final WebClient webClient;

    public UserServiceClient(@Value("${user.service-url}") String userServiceUrl) {
        this.webClient = WebClient.builder().baseUrl(userServiceUrl).build();
    }

    public Mono<SshKeyInternalResponse> getSshKey(String bearerToken, String sshKeyId) {
        return webClient.get()
                .uri("/internal/ssh-keys/{keyId}", sshKeyId)
                .header("Authorization", "Bearer " + bearerToken)
                .retrieve()
                .bodyToMono(SSH_KEY_RESPONSE_TYPE)
                .map(ApiResponse::data)
                .onErrorResume(e -> {
                    log.error("SSH 키 조회 실패: keyId={}, error={}", sshKeyId, e.getMessage());
                    return Mono.error(new VmException(VmErrorCode.SSH_KEY_FETCH_FAILED));
                });
    }
}
