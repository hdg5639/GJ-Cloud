package gj.cloud.auth.application.passwordreset.service;

public interface PasswordResetService {
    void sendCode(String email, String clientIp);
    String confirmCode(String email, String code);
    void resetPassword(String resetToken, String newPassword);
}
