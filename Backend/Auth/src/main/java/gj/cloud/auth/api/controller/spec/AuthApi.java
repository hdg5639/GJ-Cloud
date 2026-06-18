package gj.cloud.auth.api.controller.spec;

import gj.cloud.auth.application.auth.dto.LoginRequest;
import gj.cloud.auth.application.auth.dto.LoginResponse;
import gj.cloud.auth.application.auth.dto.RegisterRequest;
import gj.cloud.auth.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "회원가입 · 로그인 · 로그아웃 · 탈퇴")
@RequestMapping("/auth")
public interface AuthApi {

    @Operation(summary = "회원가입")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<Void> register(@Valid @RequestBody RegisterRequest request);

    @Operation(summary = "로그인", description = "accessToken(900s) + refreshToken 반환")
    @PostMapping("/login")
    ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request);

    @Operation(summary = "로그아웃", description = "Redis에서 refreshToken 삭제")
    @PostMapping("/logout")
    ApiResponse<Void> logout(@AuthenticationPrincipal String userId);

    @Operation(summary = "회원 탈퇴", description = "계정 및 모든 토큰 삭제")
    @DeleteMapping("/withdraw")
    ApiResponse<Void> withdraw(@AuthenticationPrincipal String userId);
}
