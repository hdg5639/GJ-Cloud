package gj.cloud.auth.application.auth.service;

import gj.cloud.auth.application.auth.dto.ChangePasswordRequest;
import gj.cloud.auth.application.auth.dto.LoginRequest;
import gj.cloud.auth.application.auth.dto.LoginResult;
import gj.cloud.auth.application.auth.dto.RegisterRequest;

public interface AuthService {
    void register(RegisterRequest request);
    LoginResult login(LoginRequest request, String clientIp);
    void logout(String userId);
    void withdraw(String userId);
    void suspendUser(String userId);
    void restoreUser(String userId);
    void changePassword(String userId, ChangePasswordRequest request, String clientIp);
}
