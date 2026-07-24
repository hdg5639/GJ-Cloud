package gj.cloud.auth.application.email.service.impl;

import gj.cloud.auth.application.email.service.EmailVerificationService;
import gj.cloud.auth.domain.user.entity.UserEntity;
import gj.cloud.auth.domain.user.repository.UserRepository;
import gj.cloud.auth.global.client.UserServiceClient;
import gj.cloud.auth.global.exception.AuthException;
import gj.cloud.auth.global.exception.enums.AuthErrorCode;
import gj.cloud.auth.global.security.EmailVerificationRateLimiter;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationServiceImpl implements EmailVerificationService {

    private static final String KEY_PREFIX = "email-verify:";
    private static final long CODE_TTL_MINUTES = 5L;

    private final StringRedisTemplate redisTemplate;
    private final JavaMailSender mailSender;
    private final UserRepository userRepository;
    private final UserServiceClient userServiceClient;
    private final EmailVerificationRateLimiter rateLimiter;

    @Value("${spring.mail.from}")
    private String mailFrom;

    @Override
    public void sendCode(String email, String clientIp) {
        userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        rateLimiter.checkAndThrowIfSendLocked(email, clientIp);

        String code = String.format("%06d", new SecureRandom().nextInt(1_000_000));
        redisTemplate.opsForValue().set(KEY_PREFIX + email, code, CODE_TTL_MINUTES, TimeUnit.MINUTES);
        rateLimiter.clearAttempts(email);
        rateLimiter.recordSend(email, clientIp);

        try {
            String template = new ClassPathResource("templates/email-verification.html")
                    .getContentAsString(StandardCharsets.UTF_8)
                    .replace("{{CODE}}", code);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(mailFrom);
            helper.setTo(email);
            helper.setSubject("[gamjabox] 이메일 인증 코드");
            helper.setText(template, true);
            mailSender.send(message);
        } catch (Exception e) {
            log.error("이메일 발송 실패 (email={}): {}", email, e.getMessage());
            throw new AuthException(AuthErrorCode.EMAIL_SEND_FAILED);
        }
    }

    @Override
    @Transactional
    public void confirmCode(String email, String code) {
        String stored = redisTemplate.opsForValue().get(KEY_PREFIX + email);
        if (stored == null) {
            throw new AuthException(AuthErrorCode.VERIFICATION_CODE_EXPIRED);
        }
        if (!stored.equals(code)) {
            // SEC-008: 시도 횟수 제한 없이는 6자리 코드를 TTL 내내 무제한으로 대입해볼 수 있었음.
            // 한도를 넘기면 코드 자체를 무효화해 재발송을 요구한다.
            if (rateLimiter.recordFailureAndCheckExceeded(email)) {
                redisTemplate.delete(KEY_PREFIX + email);
                throw new AuthException(AuthErrorCode.VERIFICATION_CODE_EXPIRED);
            }
            throw new AuthException(AuthErrorCode.INVALID_VERIFICATION_CODE);
        }

        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));
        user.activate();
        redisTemplate.delete(KEY_PREFIX + email);
        rateLimiter.clearAttempts(email);

        userServiceClient.createProfile(user.getId(), user.getEmail());
    }
}
