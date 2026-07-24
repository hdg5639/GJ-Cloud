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

// /internal/profiles/**, /internal/users/{userId}(DELETE) 전용 — Auth 서비스가 회원가입/이메일인증 시
// 프로필 생성, 회원 탈퇴 시 프로필 삭제를 요청할 때 검증하는 순수 서비스-간 신원 검증기.
// SEC-001/SEC-005: 예전에는 POST /internal/profiles가 permitAll(인증 자체가 없었음)이었고, 다른 /internal/**
// 엔드포인트도 aud만 확인해서(사용자가 exchange로 자가 발급 가능) 임의의 로그인 사용자가 직접 호출 가능했음.
// 지금은 Auth의 client-credentials로 Auth 서비스만 발급받을 수 있는 aud=user-service(자기 자신) +
// token_type=service + client_id=auth-service 토큰만 인정함 — InternalJwtValidator(ssh-key/plan 위임 조회)와
// 달리 이 경로는 최종 사용자 위임이 아니라 순수 서비스 신원만으로 판단하는 파괴적/생성 작업이라 구분함.
@Component
@RequiredArgsConstructor
public class InternalServiceJwtValidator {

    private static final String EXPECTED_AUDIENCE = "user-service";
    private static final String EXPECTED_TOKEN_TYPE = "service";
    private static final String EXPECTED_CLIENT_ID = "auth-service";

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

            String tokenType = claims.getStringClaim("token_type");
            if (!EXPECTED_TOKEN_TYPE.equals(tokenType)) {
                throw new UserException(UserErrorCode.INVALID_TOKEN);
            }

            String clientId = claims.getStringClaim("client_id");
            if (!EXPECTED_CLIENT_ID.equals(clientId)) {
                throw new UserException(UserErrorCode.INVALID_TOKEN);
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
