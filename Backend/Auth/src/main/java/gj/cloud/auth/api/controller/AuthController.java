package gj.cloud.auth.api.controller;

import gj.cloud.auth.api.controller.spec.AuthApi;
import gj.cloud.auth.application.auth.dto.LoginRequest;
import gj.cloud.auth.application.auth.dto.LoginResponse;
import gj.cloud.auth.application.auth.dto.RegisterRequest;
import gj.cloud.auth.application.auth.service.AuthService;
import gj.cloud.auth.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthController implements AuthApi {

    private final AuthService authService;

    @Override
    public ApiResponse<Void> register(RegisterRequest request) {
        authService.register(request);
        return ApiResponse.ok();
    }

    @Override
    public ApiResponse<LoginResponse> login(LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @Override
    public ApiResponse<Void> logout(@AuthenticationPrincipal String userId) {
        authService.logout(userId);
        return ApiResponse.ok();
    }

    @Override
    public ApiResponse<Void> withdraw(@AuthenticationPrincipal String userId) {
        authService.withdraw(userId);
        return ApiResponse.ok();
    }
}
