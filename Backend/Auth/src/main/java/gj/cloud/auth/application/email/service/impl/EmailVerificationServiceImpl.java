package gj.cloud.auth.application.email.service.impl;

import gj.cloud.auth.application.email.service.EmailVerificationService;
import gj.cloud.auth.domain.user.entity.UserEntity;
import gj.cloud.auth.domain.user.repository.UserRepository;
import gj.cloud.auth.global.exception.AuthException;
import gj.cloud.auth.global.exception.enums.AuthErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.security.SecureRandom;
import java.util.Map;
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
    private final RestClient restClient;

    @Value("${spring.mail.from}")
    private String mailFrom;

    @Value("${services.user-service.url:http://user:8080}")
    private String userServiceUrl;

    @Override
    public void sendCode(String email) {
        userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        String code = String.format("%06d", new SecureRandom().nextInt(1_000_000));
        redisTemplate.opsForValue().set(KEY_PREFIX + email, code, CODE_TTL_MINUTES, TimeUnit.MINUTES);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(email);
        message.setSubject("[GJ Cloud] 이메일 인증 코드");
        message.setText("인증 코드: " + code + "\n\n5분 내에 입력해주세요.");
        mailSender.send(message);
    }

    @Override
    @Transactional
    public void confirmCode(String email, String code) {
        String stored = redisTemplate.opsForValue().get(KEY_PREFIX + email);
        if (stored == null) {
            throw new AuthException(AuthErrorCode.VERIFICATION_CODE_EXPIRED);
        }
        if (!stored.equals(code)) {
            throw new AuthException(AuthErrorCode.INVALID_VERIFICATION_CODE);
        }

        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));
        user.activate();
        redisTemplate.delete(KEY_PREFIX + email);

        createUserProfile(user.getId(), user.getEmail());
    }

    private void createUserProfile(String userId, String email) {
        try {
            restClient.post()
                    .uri(userServiceUrl + "/internal/profiles")
                    .header("Content-Type", "application/json")
                    .body(Map.of("userId", userId, "email", email))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("User 서비스 프로필 생성 실패 (userId={}): {}", userId, e.getMessage());
        }
    }
}
