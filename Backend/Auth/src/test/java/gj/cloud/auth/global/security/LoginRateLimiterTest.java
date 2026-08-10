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
class LoginRateLimiterTest {

    private static final String EMAIL = "owner@gamjabox.cloud";
    private static final String IP = "203.0.113.10";

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOperations;

    private LoginRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        rateLimiter = new LoginRateLimiter(redisTemplate);
    }

    @Test
    void locksEmailAndIpPairForOneMinuteOnFirstThreshold() {
        when(valueOperations.increment(pairFailureKey())).thenReturn(5L);
        when(valueOperations.increment(ipFailureKey())).thenReturn(5L);
        when(valueOperations.increment(pairPenaltyKey())).thenReturn(1L);

        assertThatThrownBy(() -> rateLimiter.recordFailure(EMAIL.toUpperCase(java.util.Locale.ROOT), IP))
                .isInstanceOfSatisfying(AuthException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(AuthErrorCode.LOGIN_RATE_LIMIT_EXCEEDED);
                    assertThat(error.getRetryAfterSeconds()).isEqualTo(60L);
                });

        verify(redisTemplate).delete(pairFailureKey());
        verify(valueOperations).set(pairLockKey(), "1", 60L, TimeUnit.SECONDS);
        verify(redisTemplate).expire(pairPenaltyKey(), 1L, TimeUnit.HOURS);
    }

    @Test
    void escalatesRepeatedPairPenaltyToThreeMinutes() {
        when(valueOperations.increment(pairFailureKey())).thenReturn(5L);
        when(valueOperations.increment(ipFailureKey())).thenReturn(10L);
        when(valueOperations.increment(pairPenaltyKey())).thenReturn(2L);

        assertThatThrownBy(() -> rateLimiter.recordFailure(EMAIL, IP))
                .isInstanceOfSatisfying(AuthException.class,
                        error -> assertThat(error.getRetryAfterSeconds()).isEqualTo(180L));

        verify(valueOperations).set(pairLockKey(), "1", 180L, TimeUnit.SECONDS);
    }

    @Test
    void preservesGlobalIpLimitForFifteenMinuteWindow() {
        when(valueOperations.increment(pairFailureKey())).thenReturn(1L);
        when(valueOperations.increment(ipFailureKey())).thenReturn(20L);
        when(redisTemplate.getExpire(ipFailureKey(), TimeUnit.SECONDS)).thenReturn(742L);

        assertThatThrownBy(() -> rateLimiter.recordFailure(EMAIL, IP))
                .isInstanceOfSatisfying(AuthException.class,
                        error -> assertThat(error.getRetryAfterSeconds()).isEqualTo(742L));

        verify(redisTemplate).expire(pairFailureKey(), 15L, TimeUnit.MINUTES);
    }

    @Test
    void returnsRemainingPairLockTtl() {
        when(redisTemplate.getExpire(pairLockKey(), TimeUnit.SECONDS)).thenReturn(37L);
        when(valueOperations.get(ipFailureKey())).thenReturn(null);

        assertThatThrownBy(() -> rateLimiter.checkAndThrowIfLocked(EMAIL, IP))
                .isInstanceOfSatisfying(AuthException.class,
                        error -> assertThat(error.getRetryAfterSeconds()).isEqualTo(37L));
    }

    private String pairFailureKey() {
        return "login:fail:pair:" + EMAIL + ":" + IP;
    }

    private String pairLockKey() {
        return "login:lock:pair:" + EMAIL + ":" + IP;
    }

    private String pairPenaltyKey() {
        return "login:penalty:pair:" + EMAIL + ":" + IP;
    }

    private String ipFailureKey() {
        return "login:fail:ip:" + IP;
    }
}
