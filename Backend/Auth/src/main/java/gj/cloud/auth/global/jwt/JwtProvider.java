package gj.cloud.auth.global.jwt;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import gj.cloud.auth.domain.user.enums.UserRole;
import gj.cloud.auth.global.exception.AuthException;
import gj.cloud.auth.global.exception.enums.AuthErrorCode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.text.ParseException;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtProvider {

    private final JwtProperties jwtProperties;

    private RSAPrivateKey privateKey;
    private RSAPublicKey publicKey;
    private RSAKey rsaJwk;

    @PostConstruct
    public void init() throws Exception {
        KeyFactory kf = KeyFactory.getInstance("RSA");

        byte[] privBytes = Base64.getDecoder().decode(jwtProperties.getPrivateKey().replaceAll("\\s", ""));
        privateKey = (RSAPrivateKey) kf.generatePrivate(new PKCS8EncodedKeySpec(privBytes));

        byte[] pubBytes = Base64.getDecoder().decode(jwtProperties.getPublicKey().replaceAll("\\s", ""));
        publicKey = (RSAPublicKey) kf.generatePublic(new X509EncodedKeySpec(pubBytes));

        rsaJwk = new RSAKey.Builder(publicKey)
                .keyID(UUID.randomUUID().toString())
                .build();
    }

    public String issueAccessToken(String userId, String email, UserRole role, String audience) {
        try {
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(userId)
                    .audience(audience)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusMillis(jwtProperties.getAccessTokenExpiry())))
                    .claim("email", email)
                    .claim("role", role.name())
                    .build();

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).build(),
                    claims
            );
            signedJWT.sign(new RSASSASigner(privateKey));
            return signedJWT.serialize();
        } catch (JOSEException e) {
            throw new RuntimeException("JWT signing failed", e);
        }
    }

    // OPS-SEC-002: 사용자 토큰(sub=userId, email/role 클레임)과 구분되는 서비스 신원 토큰.
    // token_type=service + client_id 클레임으로 수신측(Ops)이 "진짜 서비스가 발급받은 토큰"인지
    // "사용자가 자기 토큰을 재교환한 것"인지 구분할 수 있게 함.
    public String issueServiceToken(String clientId, String audience, String scope) {
        try {
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(clientId)
                    .audience(audience)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusMillis(jwtProperties.getServiceTokenExpiry())))
                    .claim("client_id", clientId)
                    .claim("token_type", "service")
                    .claim("scope", scope)
                    .build();

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).build(),
                    claims
            );
            signedJWT.sign(new RSASSASigner(privateKey));
            return signedJWT.serialize();
        } catch (JOSEException e) {
            throw new RuntimeException("JWT signing failed", e);
        }
    }

    public JWTClaimsSet validateAndParseClaims(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            if (!signedJWT.verify(new RSASSAVerifier(publicKey))) {
                throw new AuthException(AuthErrorCode.INVALID_ACCESS_TOKEN);
            }
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            if (claims.getExpirationTime().before(new Date())) {
                throw new AuthException(AuthErrorCode.EXPIRED_ACCESS_TOKEN);
            }
            return claims;
        } catch (ParseException e) {
            throw new AuthException(AuthErrorCode.INVALID_ACCESS_TOKEN);
        } catch (JOSEException e) {
            throw new AuthException(AuthErrorCode.INVALID_ACCESS_TOKEN);
        }
    }

    public String getJwks() {
        return new JWKSet(rsaJwk).toPublicJWKSet().toString();
    }
}
