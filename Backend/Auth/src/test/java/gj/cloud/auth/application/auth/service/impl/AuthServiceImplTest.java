package gj.cloud.auth.application.auth.service.impl;

import gj.cloud.auth.application.auditlog.service.SecurityAuditLogService;
import gj.cloud.auth.application.auth.dto.LoginResult;
import gj.cloud.auth.application.auth.dto.WithdrawRequest;
import gj.cloud.auth.application.deletion.service.AccountDeletionAttemptService;
import gj.cloud.auth.application.token.service.TokenService;
import gj.cloud.auth.domain.auditlog.enums.AuditAction;
import gj.cloud.auth.domain.auditlog.enums.AuditActorType;
import gj.cloud.auth.domain.auditlog.enums.AuditResult;
import gj.cloud.auth.domain.deletion.repository.AccountDeletionJobRepository;
import gj.cloud.auth.domain.token.enums.ServiceAudience;
import gj.cloud.auth.domain.user.entity.UserEntity;
import gj.cloud.auth.domain.user.enums.UserStatus;
import gj.cloud.auth.domain.user.repository.UserRepository;
import gj.cloud.auth.global.client.UserServiceClient;
import gj.cloud.auth.global.exception.AuthException;
import gj.cloud.auth.global.exception.enums.AuthErrorCode;
import gj.cloud.auth.global.security.LoginRateLimiter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TokenService tokenService;
    @Mock private LoginRateLimiter loginRateLimiter;
    @Mock private UserServiceClient userServiceClient;
    @Mock private AccountDeletionJobRepository accountDeletionJobRepository;
    @Mock private AccountDeletionAttemptService accountDeletionAttemptService;
    @Mock private SecurityAuditLogService securityAuditLogService;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void createsNormalSessionImmediatelyAfterEmailVerification() {
        UserEntity user = UserEntity.create("new@gamjabox.cloud", "encoded-password");
        user.activate();
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(tokenService.issueAccessToken(user.getId(), user.getEmail(), user.getRole(), ServiceAudience.AUTH))
                .thenReturn("access-token");
        when(tokenService.issueRefreshToken(user.getId(), false)).thenReturn("refresh-token");

        LoginResult result = authService.createSessionAfterEmailVerification(user.getEmail(), "203.0.113.10");

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(result.cookieMaxAgeSeconds()).isEqualTo(604800L);
        verify(loginRateLimiter).clearFailures(user.getEmail(), "203.0.113.10");
        verify(securityAuditLogService).record(
                AuditActorType.USER, user.getId(), AuditAction.LOGIN,
                "USER", user.getId(), AuditResult.SUCCESS, "203.0.113.10", "EMAIL_VERIFICATION");
    }

    @Test
    void refusesToIssueSessionWhileEmailIsStillPending() {
        UserEntity user = UserEntity.create("pending@gamjabox.cloud", "encoded-password");
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.createSessionAfterEmailVerification(user.getEmail(), "203.0.113.10"))
                .isInstanceOfSatisfying(AuthException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(AuthErrorCode.EMAIL_NOT_VERIFIED));
    }

    @Test
    void withdrawsOnlyAfterCurrentPasswordMatches() {
        UserEntity user = UserEntity.create("owner@gamjabox.cloud", "encoded-password");
        user.activate();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current-password", "encoded-password")).thenReturn(true);

        authService.withdraw(user.getId(), new WithdrawRequest("current-password"), "203.0.113.20");

        assertThat(user.getStatus()).isEqualTo(UserStatus.DELETED);
        assertThat(user.getEmail()).isEqualTo("deleted_" + user.getId() + "@deleted");
        verify(loginRateLimiter).clearFailures("owner@gamjabox.cloud", "203.0.113.20");
        verify(tokenService).deleteAllUserTokens(user.getId());
        verify(accountDeletionAttemptService).attempt(org.mockito.ArgumentMatchers.any());
        verify(accountDeletionJobRepository).save(org.mockito.ArgumentMatchers.any());
        verify(securityAuditLogService).record(
                AuditActorType.USER, user.getId(), AuditAction.ACCOUNT_WITHDRAWN,
                "USER", user.getId(), AuditResult.SUCCESS, "203.0.113.20", null);
    }

    @Test
    void keepsAccountAndSessionsWhenWithdrawalPasswordIsWrong() {
        UserEntity user = UserEntity.create("owner@gamjabox.cloud", "encoded-password");
        user.activate();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.withdraw(
                user.getId(), new WithdrawRequest("wrong-password"), "203.0.113.20"))
                .isInstanceOfSatisfying(AuthException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_PASSWORD));

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getEmail()).isEqualTo("owner@gamjabox.cloud");
        verify(loginRateLimiter).recordFailure("owner@gamjabox.cloud", "203.0.113.20");
        verify(tokenService, never()).deleteAllUserTokens(user.getId());
        verifyNoInteractions(accountDeletionAttemptService, accountDeletionJobRepository);
        verify(securityAuditLogService).record(
                AuditActorType.USER, user.getId(), AuditAction.ACCOUNT_WITHDRAWN,
                "USER", user.getId(), AuditResult.FAILURE, "203.0.113.20", "INVALID_PASSWORD_CONFIRMATION");
    }
}
