package gj.cloud.auth.global.jwt;

import com.nimbusds.jwt.JWTClaimsSet;
import gj.cloud.auth.global.exception.AuthException;
import gj.cloud.auth.global.exception.enums.AuthErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.util.List;

// /internal/** 전용 — User 서비스가 계정 정지/복구 상태 동기화를 요청할 때 검증하는 순수 서비스-간
// 신원 검증기(SEC-004). Auth는 자기 자신의 키페어를 갖고 있으므로 Ops/VM/User처럼 JWKS를 HTTP로
// 가져올 필요 없이 JwtProvider.validateAndParseClaims로 서명/만료를 검증하고, 여기서는
// aud=auth-service(자기 자신) + token_type=service + client_id=user-service만 추가로 확인한다.
@Component
@RequiredArgsConstructor
public class InternalJwtValidator {

    private static final String EXPECTED_AUDIENCE = "auth-service";
    private static final String EXPECTED_TOKEN_TYPE = "service";
    private static final String EXPECTED_CLIENT_ID = "user-service";

    private final JwtProvider jwtProvider;

    public JWTClaimsSet validate(String token) {
        JWTClaimsSet claims = jwtProvider.validateAndParseClaims(token);

        List<String> audience = claims.getAudience();
        if (audience == null || !audience.contains(EXPECTED_AUDIENCE)) {
            throw new AuthException(AuthErrorCode.INVALID_AUDIENCE);
        }

        try {
            String tokenType = claims.getStringClaim("token_type");
            if (!EXPECTED_TOKEN_TYPE.equals(tokenType)) {
                throw new AuthException(AuthErrorCode.INVALID_ACCESS_TOKEN);
            }

            String clientId = claims.getStringClaim("client_id");
            if (!EXPECTED_CLIENT_ID.equals(clientId)) {
                throw new AuthException(AuthErrorCode.INVALID_ACCESS_TOKEN);
            }
        } catch (ParseException e) {
            throw new AuthException(AuthErrorCode.INVALID_ACCESS_TOKEN);
        }

        return claims;
    }
}
