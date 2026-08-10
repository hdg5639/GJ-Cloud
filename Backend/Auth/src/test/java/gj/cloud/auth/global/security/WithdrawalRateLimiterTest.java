package gj.cloud.auth.global.security;

import gj.cloud.auth.global.exception.AuthException;
import gj.cloud.auth.global.exception.enums.AuthErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WithdrawalRateLimiterTest {

    private static final String USER_ID = "user-1";
    private static final String KEY = "withdraw:fail:user:" + USER_ID;

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOperations;

    private WithdrawalRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new WithdrawalRateLimiter(redisTemplate);
    }

    @Test
    void locksWithdrawalForRemainingFiveMinuteWindowOnFifthFailure() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(KEY)).thenReturn(5L);
        when(redisTemplate.getExpire(KEY, TimeUnit.SECONDS)).thenReturn(241L);

        assertThatThrownBy(() -> rateLimiter.recordFailure(USER_ID))
                .isInstanceOfSatisfying(AuthException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(AuthErrorCode.WITHDRAWAL_RATE_LIMIT_EXCEEDED);
                    assertThat(error.getRetryAfterSeconds()).isEqualTo(241L);
                });
    }

    @Test
    void returnsRemainingWithdrawalTtlWhileLocked() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY)).thenReturn("5");
        when(redisTemplate.getExpire(KEY, TimeUnit.SECONDS)).thenReturn(83L);

        assertThatThrownBy(() -> rateLimiter.checkAndThrowIfLocked(USER_ID))
                .isInstanceOfSatisfying(AuthException.class,
                        error -> assertThat(error.getRetryAfterSeconds()).isEqualTo(83L));
    }

    @Test
    void clearsOnlyWithdrawalFailuresAfterSuccessfulConfirmation() {
        rateLimiter.clearFailures(USER_ID);

        verify(redisTemplate).delete(KEY);
    }
}
