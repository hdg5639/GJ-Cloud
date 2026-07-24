package gj.cloud.vm.global.jwt;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import gj.cloud.vm.global.config.AuthProperties;
import gj.cloud.vm.global.exception.VmException;
import gj.cloud.vm.global.exception.enums.VmErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.List;

// /internal/** 전용 — Auth 서비스가 회원 탈퇴 시 사용자 데이터 정리를 요청할 때 검증.
// SEC-001/SEC-002: 예전에는 aud=user-service(사용자가 /auth/token/exchange로 자가 발급 가능)만
// 확인해서, 임의의 로그인 사용자가 스스로 발급받은 토큰으로 이 내부 API(교차 유저 삭제)를 직접 호출할
// 수 있었음(권한 상승). 지금은 Auth의 client-credentials로 Auth 서비스만 발급받을 수 있는
// aud=vm-service(자기 자신) + token_type=service + client_id=auth-service 토큰만 인정함.
@Component
@RequiredArgsConstructor
public class InternalJwtValidator {

    private static final String EXPECTED_AUDIENCE = "vm-service";
    private static final String EXPECTED_TOKEN_TYPE = "service";
    private static final String EXPECTED_CLIENT_ID = "auth-service";

    private final AuthProperties authProperties;
    private final WebClient webClient = WebClient.create();

    private volatile Mono<RSAPublicKey> cachedKeyMono;
    private volatile long cacheLoadedAt = 0;

    public Mono<JWTClaimsSet> validate(String token) {
        return getPublicKey()
                .flatMap(publicKey -> Mono.fromCallable(() -> parseAndValidate(token, publicKey))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    private JWTClaimsSet parseAndValidate(String token, RSAPublicKey publicKey) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            if (!signedJWT.verify(new RSASSAVerifier(publicKey))) {
                throw new VmException(VmErrorCode.INVALID_TOKEN);
            }
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            if (claims.getExpirationTime() == null || claims.getExpirationTime().before(new Date())) {
                throw new VmException(VmErrorCode.INVALID_TOKEN);
            }
            List<String> audience = claims.getAudience();
            if (audience == null || !audience.contains(EXPECTED_AUDIENCE)) {
                throw new VmException(VmErrorCode.INVALID_AUDIENCE);
            }

            String tokenType = claims.getStringClaim("token_type");
            if (!EXPECTED_TOKEN_TYPE.equals(tokenType)) {
                throw new VmException(VmErrorCode.INVALID_TOKEN);
            }

            String clientId = claims.getStringClaim("client_id");
            if (!EXPECTED_CLIENT_ID.equals(clientId)) {
                throw new VmException(VmErrorCode.INVALID_TOKEN);
            }

            return claims;
        } catch (VmException e) {
            throw e;
        } catch (Exception e) {
            throw new VmException(VmErrorCode.INVALID_TOKEN);
        }
    }

    private Mono<RSAPublicKey> getPublicKey() {
        long now = System.currentTimeMillis();
        if (cachedKeyMono == null || now - cacheLoadedAt > authProperties.getJwksCacheTtl()) {
            synchronized (this) {
                if (cachedKeyMono == null || now - cacheLoadedAt > authProperties.getJwksCacheTtl()) {
                    cachedKeyMono = fetchPublicKey().cache();
                    cacheLoadedAt = now;
                }
            }
        }
        return cachedKeyMono;
    }

    private Mono<RSAPublicKey> fetchPublicKey() {
        String jwksUrl = authProperties.getServerUrl() + "/auth/.well-known/jwks.json";
        return webClient.get()
                .uri(jwksUrl)
                .retrieve()
                .bodyToMono(String.class)
                .map(jwksJson -> {
                    try {
                        JWKSet jwkSet = JWKSet.parse(jwksJson);
                        RSAKey rsaKey = (RSAKey) jwkSet.getKeys().get(0);
                        return rsaKey.toRSAPublicKey();
                    } catch (Exception e) {
                        throw new VmException(VmErrorCode.INVALID_TOKEN);
                    }
                });
    }
}
