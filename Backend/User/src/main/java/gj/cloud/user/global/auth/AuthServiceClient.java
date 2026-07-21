package gj.cloud.user.global.auth;

import gj.cloud.user.global.auth.dto.ServiceTokenRequest;
import gj.cloud.user.global.auth.dto.ServiceTokenResponse;
import gj.cloud.user.global.config.AuthProperties;
import gj.cloud.user.global.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

// SEC-004: 관리자가 ControlBox에서 계정을 정지/복구하면 User 자신의 프로필 상태뿐 아니라 Auth의
// 로그인/토큰 갱신 가능 여부도 함께 갱신되도록 동기화하는 창구. User 자신의 client-credentials로
// Auth에서 서비스 토큰을 발급받아 사용한다(VM의 ServiceTokenClient와 동일한 패턴).
@Slf4j
@Component
public class AuthServiceClient {

    private static final ParameterizedTypeReference<ApiResponse<ServiceTokenResponse>> SERVICE_TOKEN_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final AuthProperties authProperties;

    public AuthServiceClient(AuthProperties authProperties) {
        this.authProperties = authProperties;
        this.restClient = RestClient.builder().baseUrl(authProperties.getServerUrl()).build();
    }

    public void syncStatus(String userId, String status) {
        try {
            String token = requestServiceToken();
            restClient.patch()
                    .uri("/internal/users/{userId}/status", userId)
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .body(Map.of("status", status))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Auth 서비스 상태 동기화 실패 (userId={}, status={}): {}", userId, status, e.getMessage());
        }
    }

    private String requestServiceToken() {
        ApiResponse<ServiceTokenResponse> response = restClient.post()
                .uri("/auth/token/service")
                .header("Content-Type", "application/json")
                .body(new ServiceTokenRequest(authProperties.getServiceClientId(), authProperties.getServiceClientSecret()))
                .retrieve()
                .body(SERVICE_TOKEN_RESPONSE_TYPE);
        return response.data().accessToken();
    }
}
