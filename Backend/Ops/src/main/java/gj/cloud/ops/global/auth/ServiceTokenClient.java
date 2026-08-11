package gj.cloud.ops.global.auth;

import gj.cloud.ops.global.config.AuthProperties;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import gj.cloud.ops.global.response.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;

@Component
public class ServiceTokenClient {

    private static final ParameterizedTypeReference<ApiResponse<ServiceTokenResponse>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final AuthProperties authProperties;
    private final RestClient restClient;
    private final Clock clock;
    private final Object refreshLock = new Object();
    private volatile CachedToken cachedToken;

    @Autowired
    public ServiceTokenClient(AuthProperties authProperties) {
        this(authProperties, RestClient.builder(), Clock.systemUTC());
    }

    ServiceTokenClient(AuthProperties authProperties, RestClient.Builder restClientBuilder, Clock clock) {
        this.authProperties = authProperties;
        this.restClient = restClientBuilder.build();
        this.clock = clock;
    }

    public String getToken() {
        CachedToken current = cachedToken;
        if (isUsable(current)) return current.value();

        synchronized (refreshLock) {
            current = cachedToken;
            if (isUsable(current)) return current.value();
            try {
                ApiResponse<ServiceTokenResponse> response = restClient.post()
                        .uri(authProperties.getServerUrl() + "/auth/token/service")
                        .body(new ServiceTokenRequest(
                                authProperties.getServiceClientId(),
                                authProperties.getServiceClientSecret()))
                        .retrieve()
                        .body(RESPONSE_TYPE);
                if (response == null || response.data() == null || response.data().accessToken() == null) {
                    throw new IllegalStateException("서비스 토큰 응답이 비어 있습니다.");
                }
                ServiceTokenResponse token = response.data();
                cachedToken = new CachedToken(token.accessToken(), refreshAt(token.expiresIn()));
                return token.accessToken();
            } catch (Exception e) {
                throw new OpsException(OpsErrorCode.VM_CONTEXT_FETCH_FAILED);
            }
        }
    }

    private boolean isUsable(CachedToken token) {
        return token != null && clock.instant().isBefore(token.refreshAt());
    }

    private Instant refreshAt(long expiresInSeconds) {
        long skew = Math.min(30, Math.max(1, expiresInSeconds / 10));
        return clock.instant().plusSeconds(Math.max(0, expiresInSeconds - skew));
    }

    private record ServiceTokenRequest(String clientId, String clientSecret) {
    }

    private record ServiceTokenResponse(String accessToken, String tokenType, long expiresIn) {
    }

    private record CachedToken(String value, Instant refreshAt) {
    }
}
