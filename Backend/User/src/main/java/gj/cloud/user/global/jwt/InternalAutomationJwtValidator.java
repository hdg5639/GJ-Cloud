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

@Component
@RequiredArgsConstructor
public class InternalAutomationJwtValidator {

    private static final String EXPECTED_AUDIENCE = "user-service";
    private static final String EXPECTED_TOKEN_TYPE = "service";
    private static final String EXPECTED_CLIENT_ID = "vm-user-service";
    private static final String EXPECTED_SCOPE = "user:plan-read";

    private final AuthProperties authProperties;
    private final RestClient restClient = RestClient.create();

    private volatile RSAPublicKey cachedPublicKey;
    private volatile long cacheLoadedAt;

    public JWTClaimsSet validate(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            if (!signedJWT.verify(new RSASSAVerifier(getPublicKey()))) {
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
            if (!EXPECTED_TOKEN_TYPE.equals(claims.getStringClaim("token_type"))
                    || !EXPECTED_CLIENT_ID.equals(claims.getStringClaim("client_id"))
                    || !EXPECTED_SCOPE.equals(claims.getStringClaim("scope"))) {
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
                    cacheLoadedAt = now;
                }
            }
        }
        return cachedPublicKey;
    }

    private RSAPublicKey fetchPublicKey() {
        try {
            String jwksJson = restClient.get()
                    .uri(authProperties.getServerUrl() + "/auth/.well-known/jwks.json")
                    .retrieve()
                    .body(String.class);
            return ((RSAKey) JWKSet.parse(jwksJson).getKeys().get(0)).toRSAPublicKey();
        } catch (Exception e) {
            throw new UserException(UserErrorCode.INVALID_TOKEN);
        }
    }
}
