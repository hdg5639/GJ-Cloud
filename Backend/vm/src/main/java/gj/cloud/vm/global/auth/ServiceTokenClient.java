package gj.cloud.vm.global.auth;

import gj.cloud.vm.global.auth.dto.ServiceTokenRequest;
import gj.cloud.vm.global.auth.dto.ServiceTokenResponse;
import gj.cloud.vm.global.config.AuthProperties;
import gj.cloud.vm.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

// OPS-SEC-002: Ops의 /internal/** 호출 시 사용자 토큰을 그대로 전달하는 대신, VM 서비스 자신의
// client_id/client_secret으로 Auth에서 발급받은 서비스 토큰을 사용하기 위한 클라이언트.
// 호출 빈도가 낮아(VM 생성/삭제 시점) 별도 캐싱 없이 매번 새로 발급받음.
@Component
@RequiredArgsConstructor
public class ServiceTokenClient {

    private static final ParameterizedTypeReference<ApiResponse<ServiceTokenResponse>> SERVICE_TOKEN_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final AuthProperties authProperties;
    private final WebClient webClient = WebClient.create();

    public Mono<String> getToken() {
        return webClient.post()
                .uri(authProperties.getServerUrl() + "/auth/token/service")
                .bodyValue(new ServiceTokenRequest(
                        authProperties.getServiceClientId(),
                        authProperties.getServiceClientSecret()))
                .retrieve()
                .bodyToMono(SERVICE_TOKEN_RESPONSE_TYPE)
                .map(ApiResponse::data)
                .map(ServiceTokenResponse::accessToken);
    }
}
