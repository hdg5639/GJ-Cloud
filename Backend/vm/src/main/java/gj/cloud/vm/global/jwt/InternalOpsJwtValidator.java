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

@Component
@RequiredArgsConstructor
public class InternalOpsJwtValidator {

    private static final String EXPECTED_AUDIENCE = "ops-service";

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
        return webClient
                .get()
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
