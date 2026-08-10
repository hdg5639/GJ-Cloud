package gj.cloud.auth.application.auth.service.impl;

import gj.cloud.auth.application.auditlog.service.SecurityAuditLogService;
import gj.cloud.auth.application.auth.dto.ChangePasswordRequest;
import gj.cloud.auth.application.auth.dto.LoginRequest;
import gj.cloud.auth.application.auth.dto.LoginResult;
import gj.cloud.auth.application.auth.dto.RegisterRequest;
import gj.cloud.auth.application.auth.dto.WithdrawRequest;
import gj.cloud.auth.application.auth.service.AuthService;
import gj.cloud.auth.application.deletion.service.AccountDeletionAttemptService;
import gj.cloud.auth.application.token.service.TokenService;
import gj.cloud.auth.domain.auditlog.enums.AuditAction;
import gj.cloud.auth.domain.auditlog.enums.AuditActorType;
import gj.cloud.auth.domain.auditlog.enums.AuditResult;
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
    private final SecurityAuditLogService securityAuditLogService;

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
            recordLoginFailure(request.email(), clientIp, "USER_NOT_FOUND");
            throw e;
        }

        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            recordLoginFailure(user.getId(), clientIp, "EMAIL_NOT_VERIFIED");
            throw new AuthException(AuthErrorCode.EMAIL_NOT_VERIFIED);
        }
        if (user.getStatus() == UserStatus.DELETED) {
            recordLoginFailure(user.getId(), clientIp, "ACCOUNT_DELETED");
            throw new AuthException(AuthErrorCode.ACCOUNT_DELETED);
        }
        if (user.getStatus() == UserStatus.SUSPENDED) {
            recordLoginFailure(user.getId(), clientIp, "ACCOUNT_SUSPENDED");
            throw new AuthException(AuthErrorCode.ACCOUNT_SUSPENDED);
        }
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            loginRateLimiter.recordFailure(request.email(), clientIp);
            recordLoginFailure(user.getId(), clientIp, "INVALID_PASSWORD");
            throw new AuthException(AuthErrorCode.INVALID_PASSWORD);
        }

        loginRateLimiter.clearFailures(request.email(), clientIp);
        securityAuditLogService.record(AuditActorType.USER, user.getId(), AuditAction.LOGIN,
                "USER", user.getId(), AuditResult.SUCCESS, clientIp, null);

        return issueSession(user, request.rememberMe());
    }

    // 이메일 코드 확인은 메일함 소유권을 증명한 직후의 1회성 인증 절차다. 회원가입 완료 뒤 다시 로그인
    // 화면을 거치지 않고 프로필 온보딩으로 이어질 수 있도록 일반 로그인과 동일한 세션을 발급한다.
    @Override
    public LoginResult createSessionAfterEmailVerification(String email, String clientIp) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            throw new AuthException(AuthErrorCode.EMAIL_NOT_VERIFIED);
        }
        if (user.getStatus() == UserStatus.DELETED) {
            throw new AuthException(AuthErrorCode.ACCOUNT_DELETED);
        }
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new AuthException(AuthErrorCode.ACCOUNT_SUSPENDED);
        }

        loginRateLimiter.clearFailures(user.getEmail(), clientIp);
        securityAuditLogService.record(AuditActorType.USER, user.getId(), AuditAction.LOGIN,
                "USER", user.getId(), AuditResult.SUCCESS, clientIp, "EMAIL_VERIFICATION");
        return issueSession(user, false);
    }

    private LoginResult issueSession(UserEntity user, boolean rememberMe) {
        String accessToken = tokenService.issueAccessToken(
                user.getId(), user.getEmail(), user.getRole(), ServiceAudience.AUTH);
        String refreshToken = tokenService.issueRefreshToken(user.getId(), rememberMe);
        long cookieMaxAgeSeconds = rememberMe ? 2592000L : 604800L;
        return new LoginResult(accessToken, refreshToken, cookieMaxAgeSeconds);
    }

    private void recordLoginFailure(String actorId, String clientIp, String reason) {
        securityAuditLogService.record(AuditActorType.USER, actorId, AuditAction.LOGIN,
                "USER", actorId, AuditResult.FAILURE, clientIp, reason);
    }

    @Override
    public void logout(String userId) {
        tokenService.deleteAllUserTokens(userId);
    }

    // REL-001: User/VM 정리 호출은 즉시 시도하되, 실패해도 여기서 예외를 던지지 않고(Auth 쪽 탈퇴 자체는
    // 확정) job row에 결과를 남긴다. 실패분은 AccountDeletionRetryScheduler가 재시도한다.
    @Override
    @Transactional
    public void withdraw(String userId, WithdrawRequest request, String clientIp) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        loginRateLimiter.checkAndThrowIfLocked(user.getEmail(), clientIp);
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            loginRateLimiter.recordFailure(user.getEmail(), clientIp);
            securityAuditLogService.record(AuditActorType.USER, userId, AuditAction.ACCOUNT_WITHDRAWN,
                    "USER", userId, AuditResult.FAILURE, clientIp, "INVALID_PASSWORD_CONFIRMATION");
            throw new AuthException(AuthErrorCode.INVALID_PASSWORD);
        }
        loginRateLimiter.clearFailures(user.getEmail(), clientIp);

        String originalEmail = user.getEmail();
        user.anonymizeAndDelete();
        logout(userId);

        AccountDeletionJobEntity job = AccountDeletionJobEntity.create(userId, originalEmail);
        accountDeletionAttemptService.attempt(job);
        accountDeletionJobRepository.save(job);

        securityAuditLogService.record(AuditActorType.USER, userId, AuditAction.ACCOUNT_WITHDRAWN,
                "USER", userId, AuditResult.SUCCESS, clientIp, null);
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
        // 호출 주체는 User 서비스(관리자 액션 대행) — 대상 계정 자신이 아니므로 actor를 SYSTEM으로 구분
        securityAuditLogService.record(AuditActorType.SYSTEM, "user-service", AuditAction.ACCOUNT_SUSPENDED,
                "USER", userId, AuditResult.SUCCESS, null, null);
    }

    @Override
    @Transactional
    public void restoreUser(String userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));
        user.activate();
        securityAuditLogService.record(AuditActorType.SYSTEM, "user-service", AuditAction.ACCOUNT_RESTORED,
                "USER", userId, AuditResult.SUCCESS, null, null);
    }

    private void createUserProfile(String userId, String email) {
        userServiceClient.createProfile(userId, email);
    }

    // 설정 페이지의 비밀번호 변경 — 이메일 인증 없이 현재 비밀번호로만 본인 확인.
    // 성공 시 다른 기기/세션에서도 재로그인이 필요하도록 기존 refresh 토큰을 전부 무효화한다.
    @Override
    @Transactional
    public void changePassword(String userId, ChangePasswordRequest request, String clientIp) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            securityAuditLogService.record(AuditActorType.USER, userId, AuditAction.PASSWORD_CHANGED,
                    "USER", userId, AuditResult.FAILURE, clientIp, "INVALID_CURRENT_PASSWORD");
            throw new AuthException(AuthErrorCode.INVALID_PASSWORD);
        }

        user.changePassword(passwordEncoder.encode(request.newPassword()));
        tokenService.deleteAllUserTokens(userId);

        securityAuditLogService.record(AuditActorType.USER, userId, AuditAction.PASSWORD_CHANGED,
                "USER", userId, AuditResult.SUCCESS, clientIp, null);
    }
}
