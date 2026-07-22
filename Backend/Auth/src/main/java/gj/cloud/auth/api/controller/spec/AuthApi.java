package gj.cloud.auth.api.controller.spec;

import gj.cloud.auth.application.auth.dto.ChangePasswordRequest;
import gj.cloud.auth.application.auth.dto.LoginRequest;
import gj.cloud.auth.application.auth.dto.LoginResponse;
import gj.cloud.auth.application.auth.dto.RegisterRequest;
import gj.cloud.auth.application.email.dto.EmailVerifyConfirmRequest;
import gj.cloud.auth.application.email.dto.EmailVerifyRequest;
import gj.cloud.auth.application.passwordreset.dto.PasswordResetConfirmRequest;
import gj.cloud.auth.application.passwordreset.dto.PasswordResetConfirmResponse;
import gj.cloud.auth.application.passwordreset.dto.PasswordResetRequest;
import gj.cloud.auth.application.passwordreset.dto.PasswordResetSendRequest;
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
    ApiResponse<Void> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest);

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
    ApiResponse<Void> sendVerifyCode(@Valid @RequestBody EmailVerifyRequest request, HttpServletRequest httpRequest);

    @Operation(summary = "인증 코드 확인", description = "코드 일치 시 계정 ACTIVE 전환")
    @PostMapping("/email/verify/confirm")
    ApiResponse<Void> confirmVerifyCode(@Valid @RequestBody EmailVerifyConfirmRequest request);

    @Operation(summary = "비밀번호 재설정 코드 발송", description = "가입된 이메일로 6자리 재설정 코드 발송 (5분 유효)")
    @PostMapping("/password/reset/send")
    ApiResponse<Void> sendPasswordResetCode(@Valid @RequestBody PasswordResetSendRequest request, HttpServletRequest httpRequest);

    @Operation(summary = "비밀번호 재설정 코드 확인", description = "코드 확인 성공 시 5분간 유효한 1회용 재설정 토큰 발급")
    @PostMapping("/password/reset/confirm")
    ApiResponse<PasswordResetConfirmResponse> confirmPasswordResetCode(@Valid @RequestBody PasswordResetConfirmRequest request);

    @Operation(summary = "비밀번호 재설정", description = "재설정 토큰으로 새 비밀번호 설정. 성공 시 기존 세션 전체 로그아웃")
    @PostMapping("/password/reset")
    ApiResponse<Void> resetPassword(@Valid @RequestBody PasswordResetRequest request);

    @Operation(summary = "비밀번호 변경", description = "현재 비밀번호 검증 후 새 비밀번호로 변경. 성공 시 기존 세션 전체 로그아웃")
    @PostMapping("/password/change")
    ApiResponse<Void> changePassword(@AuthenticationPrincipal String userId, @Valid @RequestBody ChangePasswordRequest request, HttpServletRequest httpRequest);
}
