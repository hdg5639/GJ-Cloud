package gj.cloud.auth.api.controller;

import gj.cloud.auth.api.controller.spec.TokenApi;
import gj.cloud.auth.application.auth.dto.LoginResponse;
import gj.cloud.auth.application.auth.dto.TokenExchangeRequest;
import gj.cloud.auth.application.auth.dto.TokenRefreshRequest;
import gj.cloud.auth.application.token.dto.TokenResponse;
import gj.cloud.auth.application.token.service.TokenService;
import gj.cloud.auth.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TokenController implements TokenApi {

    private final TokenService tokenService;

    @Override
    public ApiResponse<LoginResponse> refresh(TokenRefreshRequest request) {
        return ApiResponse.ok(tokenService.refresh(request));
    }

    @Override
    public ApiResponse<TokenResponse> exchange(String authHeader, TokenExchangeRequest request) {
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        return ApiResponse.ok(tokenService.exchange(token, request));
    }

    @Override
    public ResponseEntity<String> jwks() {
        return ResponseEntity.ok(tokenService.getJwks());
    }
}
