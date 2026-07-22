package gj.cloud.auth.application.passwordreset.dto;

// resetToken은 5분간 유효한 1회용 토큰 — 이 토큰을 들고 있어야만 실제 비밀번호 변경이 가능하다.
public record PasswordResetConfirmResponse(
        String resetToken
) {}
