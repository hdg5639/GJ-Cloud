package gj.cloud.auth.api.controller.spec;

import gj.cloud.auth.application.auth.dto.LoginResponse;
import gj.cloud.auth.application.auth.dto.TokenExchangeRequest;
import gj.cloud.auth.application.auth.dto.TokenRefreshRequest;
import gj.cloud.auth.application.token.dto.TokenResponse;
import gj.cloud.auth.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Token", description = "토큰 갱신 · 교환 · JWKS")
@RequestMapping("/auth")
public interface TokenApi {

    @Operation(summary = "토큰 갱신", description = "refreshToken으로 accessToken + 새 refreshToken 재발급 (Rotation)")
    @PostMapping("/token/refresh")
    ApiResponse<LoginResponse> refresh(@Valid @RequestBody TokenRefreshRequest request);

    @Operation(summary = "서비스 토큰 교환", description = "auth-service 토큰을 대상 서비스 토큰으로 교환. targetService: user-service | vm-service")
    @PostMapping("/token/exchange")
    ApiResponse<TokenResponse> exchange(
            HttpServletRequest httpRequest,
            @Valid @RequestBody TokenExchangeRequest request);

    @Operation(summary = "JWKS 공개키 조회", description = "각 서비스가 JWT 검증에 사용하는 공개키")
    @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> jwks();
}
