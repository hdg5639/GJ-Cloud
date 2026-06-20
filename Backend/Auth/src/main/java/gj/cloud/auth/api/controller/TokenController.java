package gj.cloud.auth.api.controller;

import gj.cloud.auth.api.controller.spec.TokenApi;
import gj.cloud.auth.application.auth.dto.LoginResponse;
import gj.cloud.auth.application.auth.dto.TokenExchangeRequest;
import gj.cloud.auth.application.token.dto.RefreshResult;
import gj.cloud.auth.application.token.dto.TokenResponse;
import gj.cloud.auth.application.token.service.TokenService;
import gj.cloud.auth.global.exception.AuthException;
import gj.cloud.auth.global.exception.enums.AuthErrorCode;
import gj.cloud.auth.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TokenController implements TokenApi {

    private final TokenService tokenService;

    @Override
    public ApiResponse<LoginResponse> refresh(String refreshToken, HttpServletResponse response) {
        if (refreshToken == null) {
            throw new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }
        RefreshResult result = tokenService.refresh(refreshToken);
        response.addHeader("Set-Cookie",
                "refreshToken=" + result.newRefreshToken()
                        + "; HttpOnly; Secure; SameSite=Strict; Path=/auth/token; Max-Age=604800");
        return ApiResponse.ok(new LoginResponse(result.accessToken(), "Bearer", 900L));
    }

    @Override
    public ApiResponse<TokenResponse> exchange(HttpServletRequest httpRequest, TokenExchangeRequest request) {
        String authHeader = httpRequest.getHeader("Authorization");
        String token = authHeader != null && authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        return ApiResponse.ok(tokenService.exchange(token, request));
    }

    @Override
    public ResponseEntity<String> jwks() {
        return ResponseEntity.ok(tokenService.getJwks());
    }
}
