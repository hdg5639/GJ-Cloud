package gj.cloud.user.global.jwt;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import gj.cloud.user.global.config.AuthProperties;
import gj.cloud.user.global.exception.UserException;
import gj.cloud.user.global.exception.enums.UserErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.List;

// /internal/users/plan 전용 — VM이 사용자를 대신해 직접 호출할 때(aud=vm-service)뿐 아니라, Ops가 자동배포
// 파이프라인 중 vm에 전달한 요청을 vm이 그대로 다시 포워딩할 때(aud=ops-service, PRO 커스텀 CNAME 검증용)도
// 허용한다 — 둘 다 "원래 최종 사용자가 가진 세션을 신뢰할 수 있는 내부 서비스가 릴레이한 것"이라는 점은
// 동일하고(Auth가 RS256으로 서명한 진짜 사용자 토큰), 어느 서비스를 거쳐 왔는지만 다르다.
// InternalJwtValidator(ssh-keys/profiles-search)는 vm-service만 인정하는 좁은 범위를 그대로 유지한다 —
// 이 완화는 plan 조회 한 곳에만 적용.
@Component
@RequiredArgsConstructor
public class InternalPlanJwtValidator {

    private static final List<String> ACCEPTED_AUDIENCES = List.of("vm-service", "ops-service");

    private final AuthProperties authProperties;
    private final RestClient restClient = RestClient.create();

    private volatile RSAPublicKey cachedPublicKey;
    private volatile long cacheLoadedAt = 0;

    public JWTClaimsSet validate(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);

            RSAPublicKey publicKey = getPublicKey();
            if (!signedJWT.verify(new RSASSAVerifier(publicKey))) {
                throw new UserException(UserErrorCode.INVALID_TOKEN);
            }

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

            if (claims.getExpirationTime() == null || claims.getExpirationTime().before(new Date())) {
                throw new UserException(UserErrorCode.INVALID_TOKEN);
            }

            List<String> audience = claims.getAudience();
            boolean accepted = audience != null && audience.stream().anyMatch(ACCEPTED_AUDIENCES::contains);
            if (!accepted) {
                throw new UserException(UserErrorCode.INVALID_AUDIENCE);
            }

            return claims;
        } catch (UserException e) {
            throw e;
        } catch (Exception e) {
            throw new UserException(UserErrorCode.INVALID_TOKEN);
        }
    }

    private RSAPublicKey getPublicKey() {
        long now = System.currentTimeMillis();
        if (cachedPublicKey == null || now - cacheLoadedAt > authProperties.getJwksCacheTtl()) {
            synchronized (this) {
                if (cachedPublicKey == null || now - cacheLoadedAt > authProperties.getJwksCacheTtl()) {
                    cachedPublicKey = fetchPublicKey();
                    cacheLoadedAt = System.currentTimeMillis();
                }
            }
        }
        return cachedPublicKey;
    }

    private RSAPublicKey fetchPublicKey() {
        try {
            String jwksUrl = authProperties.getServerUrl() + "/auth/.well-known/jwks.json";
            String jwksJson = restClient.get()
                    .uri(jwksUrl)
                    .retrieve()
                    .body(String.class);

            JWKSet jwkSet = JWKSet.parse(jwksJson);
            RSAKey rsaKey = (RSAKey) jwkSet.getKeys().get(0);
            return rsaKey.toRSAPublicKey();
        } catch (Exception e) {
            throw new UserException(UserErrorCode.INVALID_TOKEN);
        }
    }
}
