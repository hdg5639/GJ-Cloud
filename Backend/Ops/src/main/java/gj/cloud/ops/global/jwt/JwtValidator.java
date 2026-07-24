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

// 공개 API(터미널 티켓 발급 등)에서 사용 — 프론트가 Auth의 token/exchange로 발급받은 aud=ops-service 토큰을 검증
@Component
@RequiredArgsConstructor
public class JwtValidator {

    private static final String EXPECTED_AUDIENCE = "ops-service";

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
