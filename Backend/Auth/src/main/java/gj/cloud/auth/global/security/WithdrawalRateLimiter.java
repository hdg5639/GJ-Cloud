package gj.cloud.auth.global.security;

import gj.cloud.auth.global.exception.AuthException;
import gj.cloud.auth.global.exception.enums.AuthErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class WithdrawalRateLimiter {

    private static final int MAX_FAILURES = 5;
    private static final long WINDOW_MINUTES = 5;

    private final StringRedisTemplate redisTemplate;

    public void checkAndThrowIfLocked(String userId) {
        if (failureCount(userId) >= MAX_FAILURES) {
            throw rateLimit(ttlSeconds(userId));
        }
    }

    public void recordFailure(String userId) {
        String key = failureKey(userId);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, WINDOW_MINUTES, TimeUnit.MINUTES);
        }
        if (count != null && count >= MAX_FAILURES) {
            throw rateLimit(ttlSeconds(userId));
        }
    }

    public void clearFailures(String userId) {
        redisTemplate.delete(failureKey(userId));
    }

    private int failureCount(String userId) {
        String value = redisTemplate.opsForValue().get(failureKey(userId));
        if (value == null) return 0;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private long ttlSeconds(String userId) {
        Long ttl = redisTemplate.getExpire(failureKey(userId), TimeUnit.SECONDS);
        return ttl == null || ttl <= 0 ? WINDOW_MINUTES * 60 : ttl;
    }

    private AuthException rateLimit(long retryAfterSeconds) {
        return new AuthException(AuthErrorCode.WITHDRAWAL_RATE_LIMIT_EXCEEDED,
                Math.max(1, retryAfterSeconds));
    }

    private String failureKey(String userId) {
        return "withdraw:fail:user:" + userId;
    }
}
