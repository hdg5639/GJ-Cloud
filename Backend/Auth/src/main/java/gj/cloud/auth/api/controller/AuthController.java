package gj.cloud.auth.api.controller;

import gj.cloud.auth.api.controller.spec.AuthApi;
import gj.cloud.auth.application.auth.dto.LoginRequest;
import gj.cloud.auth.application.auth.dto.LoginResponse;
import gj.cloud.auth.application.auth.dto.LoginResult;
import gj.cloud.auth.application.auth.dto.RegisterRequest;
import gj.cloud.auth.application.auth.service.AuthService;
import gj.cloud.auth.application.email.dto.EmailVerifyConfirmRequest;
import gj.cloud.auth.application.email.dto.EmailVerifyRequest;
import gj.cloud.auth.application.email.service.EmailVerificationService;
import gj.cloud.auth.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthController implements AuthApi {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;

    @Override
    public ApiResponse<Void> register(RegisterRequest request) {
        authService.register(request);
        emailVerificationService.sendCode(request.email());
        return ApiResponse.ok();
    }

    @Override
    public ApiResponse<LoginResponse> login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse response) {
        String clientIp = resolveClientIp(httpRequest);
        LoginResult result = authService.login(request, clientIp);
        setRefreshTokenCookie(response, result.refreshToken(), result.cookieMaxAgeSeconds());
        return ApiResponse.ok(new LoginResponse(result.accessToken(), "Bearer", 900L));
    }

    @Override
    public ApiResponse<Void> logout(@AuthenticationPrincipal String userId, HttpServletResponse response) {
        authService.logout(userId);
        clearRefreshTokenCookie(response);
        return ApiResponse.ok();
    }

    @Override
    public ApiResponse<Void> withdraw(@AuthenticationPrincipal String userId) {
        authService.withdraw(userId);
        return ApiResponse.ok();
    }

    @Override
    public ApiResponse<Void> sendVerifyCode(EmailVerifyRequest request) {
        emailVerificationService.sendCode(request.email());
        return ApiResponse.ok();
    }

    @Override
    public ApiResponse<Void> confirmVerifyCode(EmailVerifyConfirmRequest request) {
        emailVerificationService.confirmCode(request.email(), request.code());
        return ApiResponse.ok();
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken, long maxAgeSeconds) {
        response.addHeader("Set-Cookie",
                "refreshToken=" + refreshToken
                        + "; HttpOnly; Secure; SameSite=Strict; Path=/auth/token; Max-Age=" + maxAgeSeconds);
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        response.addHeader("Set-Cookie",
                "refreshToken=; HttpOnly; Secure; SameSite=Strict; Path=/auth/token; Max-Age=0");
    }
}
