package gj.cloud.auth.application.auth.service.impl;

import gj.cloud.auth.application.auth.dto.LoginRequest;
import gj.cloud.auth.application.auth.dto.LoginResult;
import gj.cloud.auth.application.auth.dto.RegisterRequest;
import gj.cloud.auth.application.auth.service.AuthService;
import gj.cloud.auth.application.token.service.TokenService;
import gj.cloud.auth.domain.token.enums.ServiceAudience;
import gj.cloud.auth.domain.user.entity.UserEntity;
import gj.cloud.auth.domain.user.enums.UserStatus;
import gj.cloud.auth.domain.user.repository.UserRepository;
import gj.cloud.auth.global.exception.AuthException;
import gj.cloud.auth.global.exception.enums.AuthErrorCode;
import gj.cloud.auth.global.security.LoginRateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final LoginRateLimiter loginRateLimiter;
    private final RestClient restClient;

    @Value("${app.email-verification.enabled:true}")
    private boolean emailVerificationEnabled;

    @Value("${app.services.user-service-url:http://user:8080}")
    private String userServiceUrl;

    @Override
    @Transactional
    public void register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new AuthException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        }
        UserEntity user = UserEntity.create(request.email(), passwordEncoder.encode(request.password()));
        if (!emailVerificationEnabled) {
            user.activate();
        }
        userRepository.save(user);
        if (!emailVerificationEnabled) {
            createUserProfile(user.getId(), user.getEmail());
        }
    }

    @Override
    public LoginResult login(LoginRequest request, String clientIp) {
        loginRateLimiter.checkAndThrowIfLocked(request.email(), clientIp);

        UserEntity user;
        try {
            user = userRepository.findByEmail(request.email())
                    .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));
        } catch (AuthException e) {
            loginRateLimiter.recordFailure(request.email(), clientIp);
            throw e;
        }

        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            throw new AuthException(AuthErrorCode.EMAIL_NOT_VERIFIED);
        }
        if (user.getStatus() == UserStatus.DELETED) {
            throw new AuthException(AuthErrorCode.ACCOUNT_DELETED);
        }
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new AuthException(AuthErrorCode.ACCOUNT_SUSPENDED);
        }
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            loginRateLimiter.recordFailure(request.email(), clientIp);
            throw new AuthException(AuthErrorCode.INVALID_PASSWORD);
        }

        loginRateLimiter.clearFailures(request.email(), clientIp);

        String accessToken = tokenService.issueAccessToken(
                user.getId(), user.getEmail(), user.getRole(), ServiceAudience.AUTH);
        String refreshToken = tokenService.issueRefreshToken(user.getId(), request.rememberMe());
        long cookieMaxAgeSeconds = request.rememberMe() ? 2592000L : 604800L;

        return new LoginResult(accessToken, refreshToken, cookieMaxAgeSeconds);
    }

    @Override
    public void logout(String userId) {
        tokenService.deleteAllUserTokens(userId);
    }

    @Override
    @Transactional
    public void withdraw(String userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));
        user.softDelete();
        logout(userId);
    }

    private void createUserProfile(String userId, String email) {
        try {
            restClient.post()
                    .uri(userServiceUrl + "/internal/profiles")
                    .header("Content-Type", "application/json")
                    .body(Map.of("userId", userId, "email", email))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("User 서비스 프로필 생성 실패 (userId={}): {}", userId, e.getMessage());
        }
    }
}
