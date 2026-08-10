package gj.cloud.auth.api.controller;

import gj.cloud.auth.application.auth.dto.LoginResponse;
import gj.cloud.auth.application.auth.dto.LoginResult;
import gj.cloud.auth.application.auth.dto.WithdrawRequest;
import gj.cloud.auth.application.auth.service.AuthService;
import gj.cloud.auth.application.email.dto.EmailVerifyConfirmRequest;
import gj.cloud.auth.application.email.service.EmailVerificationService;
import gj.cloud.auth.application.passwordreset.service.PasswordResetService;
import gj.cloud.auth.global.response.ApiResponse;
import gj.cloud.auth.global.security.ClientIpResolver;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock private AuthService authService;
    @Mock private EmailVerificationService emailVerificationService;
    @Mock private PasswordResetService passwordResetService;
    @Mock private ClientIpResolver clientIpResolver;

    @InjectMocks
    private AuthController authController;

    @Test
    void confirmsEmailAndReturnsOnboardingSessionWithRefreshCookie() {
        String email = "new@gamjabox.cloud";
        String code = "123456";
        String clientIp = "203.0.113.10";
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();
        when(clientIpResolver.resolve(httpRequest)).thenReturn(clientIp);
        when(authService.createSessionAfterEmailVerification(email, clientIp))
                .thenReturn(new LoginResult("access-token", "refresh-token", 604800L));

        ApiResponse<LoginResponse> response = authController.confirmVerifyCode(
                new EmailVerifyConfirmRequest(email, code), httpRequest, httpResponse);

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo(new LoginResponse("access-token", "Bearer", 900L));
        Cookie refreshCookie = httpResponse.getCookie("refreshToken");
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie.getValue()).isEqualTo("refresh-token");
        assertThat(refreshCookie.getPath()).isEqualTo("/auth/token");
        assertThat(refreshCookie.getMaxAge()).isEqualTo(604800);
        assertThat(refreshCookie.getSecure()).isTrue();
        assertThat(refreshCookie.isHttpOnly()).isTrue();
        assertThat(refreshCookie.getAttribute("SameSite")).isEqualTo("Strict");

        InOrder verificationThenSession = inOrder(emailVerificationService, authService);
        verificationThenSession.verify(emailVerificationService).confirmCode(email, code);
        verificationThenSession.verify(authService).createSessionAfterEmailVerification(email, clientIp);
    }

    @Test
    void withdrawsWithPasswordAndClearsRefreshCookie() {
        String userId = "user-1";
        String clientIp = "203.0.113.20";
        WithdrawRequest request = new WithdrawRequest("current-password");
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();
        when(clientIpResolver.resolve(httpRequest)).thenReturn(clientIp);

        ApiResponse<Void> response = authController.withdraw(
                userId, request, httpRequest, httpResponse);

        assertThat(response.success()).isTrue();
        verify(authService).withdraw(userId, request, clientIp);
        Cookie refreshCookie = httpResponse.getCookie("refreshToken");
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie.getValue()).isEmpty();
        assertThat(refreshCookie.getPath()).isEqualTo("/auth/token");
        assertThat(refreshCookie.getMaxAge()).isZero();
        assertThat(refreshCookie.getSecure()).isTrue();
        assertThat(refreshCookie.isHttpOnly()).isTrue();
        assertThat(refreshCookie.getAttribute("SameSite")).isEqualTo("Strict");
    }
}
