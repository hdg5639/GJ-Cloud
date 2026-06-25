package gj.cloud.auth.global.security;

import gj.cloud.auth.global.exception.AuthException;
import gj.cloud.auth.global.exception.enums.AuthErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class LoginRateLimiter {

    private static final int MAX_FAILURES_PER_EMAIL = 5;
    private static final int MAX_FAILURES_PER_IP    = 20;
    private static final long LOCK_WINDOW_MINUTES   = 15;

    private final StringRedisTemplate redisTemplate;

    public void checkAndThrowIfLocked(String email, String ip) {
        if (isLocked("email", email, MAX_FAILURES_PER_EMAIL)
                || isLocked("ip", ip, MAX_FAILURES_PER_IP)) {
            throw new AuthException(AuthErrorCode.LOGIN_RATE_LIMIT_EXCEEDED);
        }
    }

    public void recordFailure(String email, String ip) {
        increment("email", email, MAX_FAILURES_PER_EMAIL);
        increment("ip", ip, MAX_FAILURES_PER_IP);
    }

    public void clearFailures(String email, String ip) {
        redisTemplate.delete("login:fail:email:" + email);
        redisTemplate.delete("login:fail:ip:" + ip);
    }

    private boolean isLocked(String type, String key, int max) {
        String val = redisTemplate.opsForValue().get("login:fail:" + type + ":" + key);
        if (val == null) return false;
        try {
            return Integer.parseInt(val) >= max;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void increment(String type, String key, int max) {
        String redisKey = "login:fail:" + type + ":" + key;
        Long count = redisTemplate.opsForValue().increment(redisKey);
        // 첫 번째 실패 시 TTL 설정 (이후엔 TTL 유지)
        if (count != null && count == 1) {
            redisTemplate.expire(redisKey, LOCK_WINDOW_MINUTES, TimeUnit.MINUTES);
        }
        if (count != null && count >= max) {
            throw new AuthException(AuthErrorCode.LOGIN_RATE_LIMIT_EXCEEDED);
        }
    }
}
