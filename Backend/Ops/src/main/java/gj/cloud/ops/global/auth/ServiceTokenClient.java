package gj.cloud.ops.global.auth;

import gj.cloud.ops.global.config.AuthProperties;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import gj.cloud.ops.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class ServiceTokenClient {

    private static final ParameterizedTypeReference<ApiResponse<ServiceTokenResponse>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final AuthProperties authProperties;
    private final RestClient restClient = RestClient.create();

    public String getToken() {
        try {
            ApiResponse<ServiceTokenResponse> response = restClient.post()
                    .uri(authProperties.getServerUrl() + "/auth/token/service")
                    .body(new ServiceTokenRequest(
                            authProperties.getServiceClientId(),
                            authProperties.getServiceClientSecret()))
                    .retrieve()
                    .body(RESPONSE_TYPE);
            return response.data().accessToken();
        } catch (Exception e) {
            throw new OpsException(OpsErrorCode.VM_CONTEXT_FETCH_FAILED);
        }
    }

    private record ServiceTokenRequest(String clientId, String clientSecret) {
    }

    private record ServiceTokenResponse(String accessToken, String tokenType, long expiresIn) {
    }
}
