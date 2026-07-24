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

// /internal/ssh-keys/**, /internal/users/plan 전용 — VM 서비스가 최종 사용자를 대신해 호출할 때 검증.
// Ops→VM의 InternalOpsJwtValidator와 동일한 위임(delegation) 패턴: VM은 자기 자신의 client-credentials가
// 아니라 "호출한 최종 사용자가 원래 갖고 있던 aud=vm-service 토큰"을 그대로 포워딩하고, User는 그 토큰의
// sub(principal.userId())로 본인 소유 리소스(SSH 키, 플랜)만 조회한다(InternalSshKeyController 참고).
// 순수 서비스 신원 검증(token_type=service/client_id 허용목록)이 필요한 프로필 생성/삭제는
// InternalServiceJwtValidator가 별도로 담당.
@Component
@RequiredArgsConstructor
public class InternalJwtValidator {

    private static final String EXPECTED_AUDIENCE = "vm-service";

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
            if (audience == null || !audience.contains(EXPECTED_AUDIENCE)) {
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
