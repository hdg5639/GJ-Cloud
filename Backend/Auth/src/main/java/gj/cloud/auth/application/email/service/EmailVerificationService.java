package gj.cloud.auth.application.email.service;

public interface EmailVerificationService {
    void sendCode(String email, String clientIp);
    void confirmCode(String email, String code);
}
