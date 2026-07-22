package gj.cloud.vm.application.ssh.client;

import gj.cloud.vm.application.org.dto.MemberSearchResult;
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

import java.util.List;

@Slf4j
@Component
public class UserServiceClient {

    private static final ParameterizedTypeReference<ApiResponse<SshKeyInternalResponse>> SSH_KEY_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<ApiResponse<String>> STRING_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<ApiResponse<List<MemberSearchResult>>> SEARCH_RESPONSE_TYPE =
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

    // 조직 초대용 사용자 검색 — 위임 패턴(요청한 조직 ADMIN의 토큰을 그대로 전달). 검색 자체는
    // "본인 리소스" 조회가 아니지만, vm이 이미 호출 전에 조직 ADMIN 권한을 확인하므로 User 입장에선
    // "vm을 거쳐 온 정상 로그인 사용자"라는 사실만 확인하면 충분하다.
    public Mono<List<MemberSearchResult>> searchUsers(String bearerToken, String query) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/internal/profiles/search").queryParam("query", query).build())
                .header("Authorization", "Bearer " + bearerToken)
                .retrieve()
                .bodyToMono(SEARCH_RESPONSE_TYPE)
                .map(ApiResponse::data)
                .onErrorResume(e -> {
                    log.error("사용자 검색 실패: query={}, error={}", query, e.getMessage());
                    return Mono.just(List.of());
                });
    }

    public Mono<String> getUserPlan(String bearerToken) {
        return webClient.get()
                .uri("/internal/users/plan")
                .header("Authorization", "Bearer " + bearerToken)
                .retrieve()
                .bodyToMono(STRING_RESPONSE_TYPE)
                .map(ApiResponse::data)
                .onErrorResume(e -> {
                    log.error("플랜 조회 실패: error={}", e.getMessage());
                    return Mono.just("FREE");
                });
    }
}
