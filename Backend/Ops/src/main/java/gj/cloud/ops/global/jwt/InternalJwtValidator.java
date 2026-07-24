package gj.cloud.ops.global.jwt;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import gj.cloud.ops.global.config.AuthProperties;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.List;

// /internal/** 전용 — VM 서비스가 관리 키 발급/폐기를 요청할 때 검증.
// OPS-SEC-002: 예전에는 aud=vm-service(사용자가 자기 토큰을 재교환해 자가 발급 가능)만 확인해서,
// 임의의 로그인 사용자가 /auth/token/exchange로 vm-service 토큰을 스스로 발급받아 이 내부 API를 직접
// 호출할 수 있었음(권한 상승). 지금은 Auth의 client-credentials(/auth/token/service)로 VM 서비스만
// 발급받을 수 있는 aud=ops-service + token_type=service + client_id=vm-service 토큰만 인정함.
@Component
@RequiredArgsConstructor
public class InternalJwtValidator {

    private static final String EXPECTED_AUDIENCE = "ops-service";
    private static final String EXPECTED_TOKEN_TYPE = "service";
    private static final String EXPECTED_CLIENT_ID = "vm-service";

    private final AuthProperties authProperties;
    private final RestClient restClient = RestClient.create();

    private volatile RSAPublicKey cachedPublicKey;
    private volatile long cacheLoadedAt = 0;

    public JWTClaimsSet validate(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);

            RSAPublicKey publicKey = getPublicKey();
            if (!signedJWT.verify(new RSASSAVerifier(publicKey))) {
                throw new OpsException(OpsErrorCode.INVALID_TOKEN);
            }

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

            if (claims.getExpirationTime() == null || claims.getExpirationTime().before(new Date())) {
                throw new OpsException(OpsErrorCode.INVALID_TOKEN);
            }

            List<String> audience = claims.getAudience();
            if (audience == null || !audience.contains(EXPECTED_AUDIENCE)) {
                throw new OpsException(OpsErrorCode.INVALID_AUDIENCE);
            }

            String tokenType = claims.getStringClaim("token_type");
            if (!EXPECTED_TOKEN_TYPE.equals(tokenType)) {
                throw new OpsException(OpsErrorCode.INVALID_TOKEN);
            }

            String clientId = claims.getStringClaim("client_id");
            if (!EXPECTED_CLIENT_ID.equals(clientId)) {
                throw new OpsException(OpsErrorCode.INVALID_TOKEN);
            }

            return claims;
        } catch (OpsException e) {
            throw e;
        } catch (Exception e) {
            throw new OpsException(OpsErrorCode.INVALID_TOKEN);
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
            throw new OpsException(OpsErrorCode.INVALID_TOKEN);
        }
    }
}
