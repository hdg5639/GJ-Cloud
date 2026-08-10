package gj.cloud.auth.api.controller;

import gj.cloud.auth.api.controller.spec.AuthApi;
import gj.cloud.auth.application.auth.dto.ChangePasswordRequest;
import gj.cloud.auth.application.auth.dto.LoginRequest;
import gj.cloud.auth.application.auth.dto.LoginResponse;
import gj.cloud.auth.application.auth.dto.LoginResult;
import gj.cloud.auth.application.auth.dto.RegisterRequest;
import gj.cloud.auth.application.auth.dto.WithdrawRequest;
import gj.cloud.auth.application.auth.service.AuthService;
import gj.cloud.auth.application.email.dto.EmailVerifyConfirmRequest;
import gj.cloud.auth.application.email.dto.EmailVerifyRequest;
import gj.cloud.auth.application.email.service.EmailVerificationService;
import gj.cloud.auth.application.passwordreset.dto.PasswordResetConfirmRequest;
import gj.cloud.auth.application.passwordreset.dto.PasswordResetConfirmResponse;
import gj.cloud.auth.application.passwordreset.dto.PasswordResetRequest;
import gj.cloud.auth.application.passwordreset.dto.PasswordResetSendRequest;
import gj.cloud.auth.application.passwordreset.service.PasswordResetService;
import gj.cloud.auth.global.response.ApiResponse;
import gj.cloud.auth.global.security.ClientIpResolver;
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
    private final PasswordResetService passwordResetService;
    private final ClientIpResolver clientIpResolver;

    @Override
    public ApiResponse<Void> register(RegisterRequest request, HttpServletRequest httpRequest) {
        authService.register(request);
        emailVerificationService.sendCode(request.email(), clientIpResolver.resolve(httpRequest));
        return ApiResponse.ok();
    }

    @Override
    public ApiResponse<LoginResponse> login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse response) {
        String clientIp = clientIpResolver.resolve(httpRequest);
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
    public ApiResponse<Void> withdraw(
            @AuthenticationPrincipal String userId,
            WithdrawRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response
    ) {
        authService.withdraw(userId, request, clientIpResolver.resolve(httpRequest));
        clearRefreshTokenCookie(response);
        return ApiResponse.ok();
    }

    @Override
    public ApiResponse<Void> sendVerifyCode(EmailVerifyRequest request, HttpServletRequest httpRequest) {
        emailVerificationService.sendCode(request.email(), clientIpResolver.resolve(httpRequest));
        return ApiResponse.ok();
    }

    @Override
    public ApiResponse<LoginResponse> confirmVerifyCode(
            EmailVerifyConfirmRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response
    ) {
        emailVerificationService.confirmCode(request.email(), request.code());
        LoginResult result = authService.createSessionAfterEmailVerification(
                request.email(), clientIpResolver.resolve(httpRequest));
        setRefreshTokenCookie(response, result.refreshToken(), result.cookieMaxAgeSeconds());
        return ApiResponse.ok(new LoginResponse(result.accessToken(), "Bearer", 900L));
    }

    @Override
    public ApiResponse<Void> sendPasswordResetCode(PasswordResetSendRequest request, HttpServletRequest httpRequest) {
        passwordResetService.sendCode(request.email(), clientIpResolver.resolve(httpRequest));
        return ApiResponse.ok();
    }

    @Override
    public ApiResponse<PasswordResetConfirmResponse> confirmPasswordResetCode(PasswordResetConfirmRequest request) {
        String resetToken = passwordResetService.confirmCode(request.email(), request.code());
        return ApiResponse.ok(new PasswordResetConfirmResponse(resetToken));
    }

    @Override
    public ApiResponse<Void> resetPassword(PasswordResetRequest request) {
        passwordResetService.resetPassword(request.resetToken(), request.newPassword());
        return ApiResponse.ok();
    }

    @Override
    public ApiResponse<Void> changePassword(@AuthenticationPrincipal String userId, ChangePasswordRequest request, HttpServletRequest httpRequest) {
        authService.changePassword(userId, request, clientIpResolver.resolve(httpRequest));
        return ApiResponse.ok();
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
