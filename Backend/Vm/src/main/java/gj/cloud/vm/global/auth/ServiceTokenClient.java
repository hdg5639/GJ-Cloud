package gj.cloud.vm.global.auth;

import gj.cloud.vm.global.auth.dto.ServiceTokenRequest;
import gj.cloud.vm.global.auth.dto.ServiceTokenResponse;
import gj.cloud.vm.global.config.AuthProperties;
import gj.cloud.vm.global.response.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

// OPS-SEC-002: Ops의 /internal/** 호출 시 사용자 토큰을 그대로 전달하는 대신, VM 서비스 자신의
// client_id/client_secret으로 Auth에서 발급받은 서비스 토큰을 사용하기 위한 클라이언트.
@Component
public class ServiceTokenClient {

    private static final ParameterizedTypeReference<ApiResponse<ServiceTokenResponse>> SERVICE_TOKEN_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final AuthProperties authProperties;
    private final WebClient webClient;
    private final Clock clock;
    private final ConcurrentMap<String, CachedToken> cache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Mono<String>> refreshes = new ConcurrentHashMap<>();

    @Autowired
    public ServiceTokenClient(AuthProperties authProperties) {
        this(authProperties, WebClient.create(), Clock.systemUTC());
    }

    ServiceTokenClient(AuthProperties authProperties, WebClient webClient, Clock clock) {
        this.authProperties = authProperties;
        this.webClient = webClient;
        this.clock = clock;
    }

    public Mono<String> getToken() {
        return getToken(authProperties.getServiceClientId());
    }

    public Mono<String> getToken(String clientId) {
        return Mono.defer(() -> {
            CachedToken current = cache.get(clientId);
            if (current != null && clock.instant().isBefore(current.refreshAt())) {
                return Mono.just(current.value());
            }
            return refreshes.computeIfAbsent(clientId, this::refreshToken);
        });
    }

    private Mono<String> refreshToken(String clientId) {
        return webClient.post()
                .uri(authProperties.getServerUrl() + "/auth/token/service")
                .bodyValue(new ServiceTokenRequest(
                        clientId,
                        authProperties.getServiceClientSecret()))
                .retrieve()
                .bodyToMono(SERVICE_TOKEN_RESPONSE_TYPE)
                .flatMap(response -> response.data() == null
                        ? Mono.error(new IllegalStateException("서비스 토큰 응답이 비어 있습니다."))
                        : Mono.just(response.data()))
                .map(response -> {
                    if (response.accessToken() == null) {
                        throw new IllegalStateException("서비스 토큰이 비어 있습니다.");
                    }
                    cache.put(clientId, new CachedToken(response.accessToken(), refreshAt(response.expiresIn())));
                    return response.accessToken();
                })
                .doFinally(signal -> refreshes.remove(clientId))
                .cache();
    }

    private Instant refreshAt(long expiresInSeconds) {
        long skew = Math.min(30, Math.max(1, expiresInSeconds / 10));
        return clock.instant().plusSeconds(Math.max(0, expiresInSeconds - skew));
    }

    private record CachedToken(String value, Instant refreshAt) {
    }
}
