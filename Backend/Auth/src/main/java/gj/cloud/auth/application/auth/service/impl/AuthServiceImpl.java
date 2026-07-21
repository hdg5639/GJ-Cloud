package gj.cloud.auth.application.auth.service.impl;

import gj.cloud.auth.application.auth.dto.LoginRequest;
import gj.cloud.auth.application.auth.dto.LoginResult;
import gj.cloud.auth.application.auth.dto.RegisterRequest;
import gj.cloud.auth.application.auth.service.AuthService;
import gj.cloud.auth.application.deletion.service.AccountDeletionAttemptService;
import gj.cloud.auth.application.token.service.TokenService;
import gj.cloud.auth.domain.deletion.entity.AccountDeletionJobEntity;
import gj.cloud.auth.domain.deletion.repository.AccountDeletionJobRepository;
import gj.cloud.auth.domain.token.enums.ServiceAudience;
import gj.cloud.auth.domain.user.entity.UserEntity;
import gj.cloud.auth.domain.user.enums.UserStatus;
import gj.cloud.auth.domain.user.repository.UserRepository;
import gj.cloud.auth.global.client.UserServiceClient;
import gj.cloud.auth.global.exception.AuthException;
import gj.cloud.auth.global.exception.enums.AuthErrorCode;
import gj.cloud.auth.global.security.LoginRateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final LoginRateLimiter loginRateLimiter;
    private final UserServiceClient userServiceClient;
    private final AccountDeletionJobRepository accountDeletionJobRepository;
    private final AccountDeletionAttemptService accountDeletionAttemptService;

    @Value("${app.email-verification.enabled:true}")
    private boolean emailVerificationEnabled;

    @Override
    @Transactional
    public void register(RegisterRequest request) {
        if (userRepository.existsByEmailAndStatusNot(request.email(), UserStatus.DELETED)) {
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

    // REL-001: User/VM 정리 호출은 즉시 시도하되, 실패해도 여기서 예외를 던지지 않고(Auth 쪽 탈퇴 자체는
    // 확정) job row에 결과를 남긴다. 실패분은 AccountDeletionRetryScheduler가 재시도한다.
    @Override
    @Transactional
    public void withdraw(String userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));
        String originalEmail = user.getEmail();
        user.anonymizeAndDelete();
        logout(userId);

        AccountDeletionJobEntity job = AccountDeletionJobEntity.create(userId, originalEmail);
        accountDeletionAttemptService.attempt(job);
        accountDeletionJobRepository.save(job);
    }

    // SEC-004: User 서비스가 정지/복구 시 호출. Auth가 계정 접근 상태의 단일 진실 공급원이므로
    // 여기서 상태를 갱신하고, 정지 시에는 기존 세션(refresh/exchange 캐시)을 즉시 전부 무효화한다.
    @Override
    @Transactional
    public void suspendUser(String userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));
        user.suspend();
        tokenService.deleteAllUserTokens(userId);
    }

    @Override
    @Transactional
    public void restoreUser(String userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));
        user.activate();
    }

    private void createUserProfile(String userId, String email) {
        userServiceClient.createProfile(userId, email);
    }
}
