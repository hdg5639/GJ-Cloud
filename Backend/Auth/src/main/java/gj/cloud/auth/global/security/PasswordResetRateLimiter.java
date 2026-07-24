package gj.cloud.auth.global.security;

import gj.cloud.auth.global.exception.AuthException;
import gj.cloud.auth.global.exception.enums.AuthErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

// EmailVerificationRateLimiter와 동일한 Redis 카운터 패턴을 비밀번호 재설정 코드 발송/확인에
// 별도 키 네임스페이스로 적용 — 회원가입 인증 카운터와 서로 영향을 주지 않도록 분리.
@Component
@RequiredArgsConstructor
public class PasswordResetRateLimiter {

    private static final long RESEND_COOLDOWN_SECONDS = 60;
    private static final int MAX_SENDS_PER_HOUR = 5;
    private static final int MAX_CONFIRM_ATTEMPTS = 5;

    private final StringRedisTemplate redisTemplate;

    public void checkAndThrowIfSendLocked(String email, String ip) {
        if (redisTemplate.hasKey("pwd-reset:cooldown:" + email)) {
            throw new AuthException(AuthErrorCode.PASSWORD_RESET_RATE_LIMIT_EXCEEDED);
        }
        if (isOverLimit("pwd-reset:hourly:email:" + email, MAX_SENDS_PER_HOUR)
                || isOverLimit("pwd-reset:hourly:ip:" + ip, MAX_SENDS_PER_HOUR)) {
            throw new AuthException(AuthErrorCode.PASSWORD_RESET_RATE_LIMIT_EXCEEDED);
        }
    }

    public void recordSend(String email, String ip) {
        redisTemplate.opsForValue().set("pwd-reset:cooldown:" + email, "1", RESEND_COOLDOWN_SECONDS, TimeUnit.SECONDS);
        increment("pwd-reset:hourly:email:" + email);
        increment("pwd-reset:hourly:ip:" + ip);
    }

    public boolean recordFailureAndCheckExceeded(String email) {
        String key = "pwd-reset:attempts:" + email;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, 5, TimeUnit.MINUTES);
        }
        return count != null && count >= MAX_CONFIRM_ATTEMPTS;
    }

    public void clearAttempts(String email) {
        redisTemplate.delete("pwd-reset:attempts:" + email);
    }

    private boolean isOverLimit(String key, int max) {
        String val = redisTemplate.opsForValue().get(key);
        if (val == null) return false;
        try {
            return Integer.parseInt(val) >= max;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void increment(String key) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, 1, TimeUnit.HOURS);
        }
    }
}
