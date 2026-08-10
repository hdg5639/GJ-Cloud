package gj.cloud.auth.global.exception;

import gj.cloud.auth.global.exception.enums.AuthErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    @Test
    void exposesRetryAfterHeaderForRateLimitErrors() {
        var response = new GlobalExceptionHandler().handleAuthException(
                new AuthException(AuthErrorCode.LOGIN_RATE_LIMIT_EXCEEDED, 73L));

        assertThat(response.getStatusCode().value()).isEqualTo(429);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("73");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo("LOGIN_RATE_LIMIT_EXCEEDED");
    }
}
