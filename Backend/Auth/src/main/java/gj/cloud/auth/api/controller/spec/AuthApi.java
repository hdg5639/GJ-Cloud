package gj.cloud.auth.api.controller.spec;

import gj.cloud.auth.application.auth.dto.LoginRequest;
import gj.cloud.auth.application.auth.dto.LoginResponse;
import gj.cloud.auth.application.auth.dto.RegisterRequest;
import gj.cloud.auth.application.email.dto.EmailVerifyConfirmRequest;
import gj.cloud.auth.application.email.dto.EmailVerifyRequest;
import gj.cloud.auth.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "회원가입 · 로그인 · 로그아웃 · 탈퇴 · 이메일 인증")
@RequestMapping("/auth")
public interface AuthApi {

    @Operation(summary = "회원가입", description = "가입 후 이메일로 인증 코드 자동 발송")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<Void> register(@Valid @RequestBody RegisterRequest request);

    @Operation(summary = "로그인", description = "accessToken(Body) + refreshToken(httpOnly Cookie) 발급")
    @PostMapping("/login")
    ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse response);

    @Operation(summary = "로그아웃", description = "Redis refreshToken 삭제 + 쿠키 만료")
    @PostMapping("/logout")
    ApiResponse<Void> logout(@AuthenticationPrincipal String userId, HttpServletResponse response);

    @Operation(summary = "회원 탈퇴", description = "계정 및 모든 토큰 삭제")
    @DeleteMapping("/withdraw")
    ApiResponse<Void> withdraw(@AuthenticationPrincipal String userId);

    @Operation(summary = "인증 코드 발송", description = "이메일로 6자리 코드 발송 (5분 유효)")
    @PostMapping("/email/verify/send")
    ApiResponse<Void> sendVerifyCode(@Valid @RequestBody EmailVerifyRequest request);

    @Operation(summary = "인증 코드 확인", description = "코드 일치 시 계정 ACTIVE 전환")
    @PostMapping("/email/verify/confirm")
    ApiResponse<Void> confirmVerifyCode(@Valid @RequestBody EmailVerifyConfirmRequest request);
}
