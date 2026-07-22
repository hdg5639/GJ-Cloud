package gj.cloud.auth.application.passwordreset.service.impl;

import gj.cloud.auth.application.auditlog.service.SecurityAuditLogService;
import gj.cloud.auth.application.passwordreset.service.PasswordResetService;
import gj.cloud.auth.application.token.service.TokenService;
import gj.cloud.auth.domain.auditlog.enums.AuditAction;
import gj.cloud.auth.domain.auditlog.enums.AuditActorType;
import gj.cloud.auth.domain.auditlog.enums.AuditResult;
import gj.cloud.auth.domain.user.entity.UserEntity;
import gj.cloud.auth.domain.user.repository.UserRepository;
import gj.cloud.auth.global.exception.AuthException;
import gj.cloud.auth.global.exception.enums.AuthErrorCode;
import gj.cloud.auth.global.security.PasswordResetRateLimiter;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetServiceImpl implements PasswordResetService {

    private static final String CODE_KEY_PREFIX = "pwd-reset:code:";
    private static final String TOKEN_KEY_PREFIX = "pwd-reset:token:";
    private static final long CODE_TTL_MINUTES = 5L;
    private static final long TOKEN_TTL_MINUTES = 5L;

    private final StringRedisTemplate redisTemplate;
    private final JavaMailSender mailSender;
    private final UserRepository userRepository;
    private final PasswordResetRateLimiter rateLimiter;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final SecurityAuditLogService securityAuditLogService;

    @Value("${spring.mail.from}")
    private String mailFrom;

    @Override
    public void sendCode(String email, String clientIp) {
        userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        rateLimiter.checkAndThrowIfSendLocked(email, clientIp);

        String code = String.format("%06d", new SecureRandom().nextInt(1_000_000));
        redisTemplate.opsForValue().set(CODE_KEY_PREFIX + email, code, CODE_TTL_MINUTES, TimeUnit.MINUTES);
        rateLimiter.clearAttempts(email);
        rateLimiter.recordSend(email, clientIp);

        try {
            String template = new ClassPathResource("templates/password-reset.html")
                    .getContentAsString(StandardCharsets.UTF_8)
                    .replace("{{CODE}}", code);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(mailFrom);
            helper.setTo(email);
            helper.setSubject("[gamjabox] 비밀번호 재설정 코드");
            helper.setText(template, true);
            mailSender.send(message);
        } catch (Exception e) {
            log.error("비밀번호 재설정 이메일 발송 실패 (email={}): {}", email, e.getMessage());
            throw new AuthException(AuthErrorCode.EMAIL_SEND_FAILED);
        }
    }

    @Override
    public String confirmCode(String email, String code) {
        String stored = redisTemplate.opsForValue().get(CODE_KEY_PREFIX + email);
        if (stored == null) {
            throw new AuthException(AuthErrorCode.VERIFICATION_CODE_EXPIRED);
        }
        if (!stored.equals(code)) {
            if (rateLimiter.recordFailureAndCheckExceeded(email)) {
                redisTemplate.delete(CODE_KEY_PREFIX + email);
                throw new AuthException(AuthErrorCode.VERIFICATION_CODE_EXPIRED);
            }
            throw new AuthException(AuthErrorCode.INVALID_VERIFICATION_CODE);
        }

        userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        redisTemplate.delete(CODE_KEY_PREFIX + email);
        rateLimiter.clearAttempts(email);

        // 코드 확인 성공 시점에 바로 새 비번을 받지 않고, 5분짜리 1회용 토큰을 발급해 별도 요청으로
        // 분리 — 코드 자체가 새 비밀번호 제출 시점까지 재사용 가능한 상태로 남아있지 않도록 한다.
        String resetToken = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(TOKEN_KEY_PREFIX + resetToken, email, TOKEN_TTL_MINUTES, TimeUnit.MINUTES);
        return resetToken;
    }

    @Override
    @Transactional
    public void resetPassword(String resetToken, String newPassword) {
        String email = redisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + resetToken);
        if (email == null) {
            throw new AuthException(AuthErrorCode.INVALID_RESET_TOKEN);
        }
        redisTemplate.delete(TOKEN_KEY_PREFIX + resetToken);

        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));
        user.changePassword(passwordEncoder.encode(newPassword));
        tokenService.deleteAllUserTokens(user.getId());

        securityAuditLogService.record(AuditActorType.USER, user.getId(), AuditAction.PASSWORD_RESET,
                "USER", user.getId(), AuditResult.SUCCESS, null, null);
    }
}
