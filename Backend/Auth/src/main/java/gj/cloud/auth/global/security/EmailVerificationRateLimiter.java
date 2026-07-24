package gj.cloud.auth.global.security;

import gj.cloud.auth.global.exception.AuthException;
import gj.cloud.auth.global.exception.enums.AuthErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

// SEC-008: 이메일 인증 코드 발송/확인에 재발송 쿨다운, 시간당 발송 한도, 확인 시도 횟수 제한이
// 전혀 없어 이메일 폭탄 발송이나 6자리 코드(100만 경우의 수) 무제한 시도가 가능했음.
// LoginRateLimiter와 동일한 Redis 카운터 패턴을 재사용.
@Component
@RequiredArgsConstructor
public class EmailVerificationRateLimiter {

    private static final long RESEND_COOLDOWN_SECONDS = 60;
    private static final int MAX_SENDS_PER_HOUR = 5;
    private static final int MAX_CONFIRM_ATTEMPTS = 5;

    private final StringRedisTemplate redisTemplate;

    public void checkAndThrowIfSendLocked(String email, String ip) {
        if (redisTemplate.hasKey("email-verify:cooldown:" + email)) {
            throw new AuthException(AuthErrorCode.EMAIL_VERIFY_RATE_LIMIT_EXCEEDED);
        }
        if (isOverLimit("email-verify:hourly:email:" + email, MAX_SENDS_PER_HOUR)
                || isOverLimit("email-verify:hourly:ip:" + ip, MAX_SENDS_PER_HOUR)) {
            throw new AuthException(AuthErrorCode.EMAIL_VERIFY_RATE_LIMIT_EXCEEDED);
        }
    }

    public void recordSend(String email, String ip) {
        redisTemplate.opsForValue().set("email-verify:cooldown:" + email, "1", RESEND_COOLDOWN_SECONDS, TimeUnit.SECONDS);
        increment("email-verify:hourly:email:" + email);
        increment("email-verify:hourly:ip:" + ip);
    }

    // 시도 횟수 초과 시 코드 자체를 무효화(재발송 필요)하도록 호출자가 코드 삭제를 병행해야 함
    public boolean recordFailureAndCheckExceeded(String email) {
        String key = "email-verify:attempts:" + email;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, 5, TimeUnit.MINUTES);
        }
        return count != null && count >= MAX_CONFIRM_ATTEMPTS;
    }

    public void clearAttempts(String email) {
        redisTemplate.delete("email-verify:attempts:" + email);
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
