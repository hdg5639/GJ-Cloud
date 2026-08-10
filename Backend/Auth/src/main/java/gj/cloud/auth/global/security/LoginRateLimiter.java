package gj.cloud.auth.global.security;

import gj.cloud.auth.global.exception.AuthException;
import gj.cloud.auth.global.exception.enums.AuthErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class LoginRateLimiter {

    private static final int MAX_FAILURES_PER_PAIR = 5;
    private static final int MAX_FAILURES_PER_IP = 20;
    private static final long FAILURE_WINDOW_MINUTES = 15;
    private static final long PENALTY_WINDOW_HOURS = 1;
    private static final long[] PAIR_COOLDOWN_SECONDS = {60, 180, 300};

    private final StringRedisTemplate redisTemplate;

    public void checkAndThrowIfLocked(String email, String ip) {
        long pairRetryAfter = ttlSeconds(pairLockKey(email, ip));
        long ipRetryAfter = isAtLeast(ipFailureKey(ip), MAX_FAILURES_PER_IP)
                ? ttlSeconds(ipFailureKey(ip)) : 0;
        long retryAfter = Math.max(pairRetryAfter, ipRetryAfter);
        if (retryAfter > 0) {
            throw rateLimit(retryAfter);
        }
    }

    public void recordFailure(String email, String ip) {
        long pairFailures = incrementWithInitialTtl(pairFailureKey(email, ip), FAILURE_WINDOW_MINUTES, TimeUnit.MINUTES);
        long ipFailures = incrementWithInitialTtl(ipFailureKey(ip), FAILURE_WINDOW_MINUTES, TimeUnit.MINUTES);
        long retryAfter = 0;

        if (pairFailures >= MAX_FAILURES_PER_PAIR) {
            redisTemplate.delete(pairFailureKey(email, ip));
            long penaltyLevel = incrementWithInitialTtl(
                    pairPenaltyKey(email, ip), PENALTY_WINDOW_HOURS, TimeUnit.HOURS);
            long cooldown = PAIR_COOLDOWN_SECONDS[(int) Math.min(
                    penaltyLevel - 1, PAIR_COOLDOWN_SECONDS.length - 1)];
            redisTemplate.opsForValue().set(pairLockKey(email, ip), "1", cooldown, TimeUnit.SECONDS);
            retryAfter = cooldown;
        }

        if (ipFailures >= MAX_FAILURES_PER_IP) {
            retryAfter = Math.max(retryAfter, ttlSeconds(ipFailureKey(ip)));
        }
        if (retryAfter > 0) {
            throw rateLimit(retryAfter);
        }
    }

    public void clearFailures(String email, String ip) {
        redisTemplate.delete(java.util.List.of(
                pairFailureKey(email, ip),
                pairLockKey(email, ip),
                pairPenaltyKey(email, ip)
        ));
    }

    private boolean isAtLeast(String key, int max) {
        String val = redisTemplate.opsForValue().get(key);
        if (val == null) return false;
        try {
            return Integer.parseInt(val) >= max;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private long incrementWithInitialTtl(String key, long timeout, TimeUnit unit) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, timeout, unit);
        }
        return count == null ? 0 : count;
    }

    private long ttlSeconds(String key) {
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return ttl == null || ttl <= 0 ? 0 : ttl;
    }

    private AuthException rateLimit(long retryAfterSeconds) {
        return new AuthException(AuthErrorCode.LOGIN_RATE_LIMIT_EXCEEDED, Math.max(1, retryAfterSeconds));
    }

    private String pairFailureKey(String email, String ip) {
        return "login:fail:pair:" + normalizeEmail(email) + ":" + ip;
    }

    private String pairLockKey(String email, String ip) {
        return "login:lock:pair:" + normalizeEmail(email) + ":" + ip;
    }

    private String pairPenaltyKey(String email, String ip) {
        return "login:penalty:pair:" + normalizeEmail(email) + ":" + ip;
    }

    private String ipFailureKey(String ip) {
        return "login:fail:ip:" + ip;
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
