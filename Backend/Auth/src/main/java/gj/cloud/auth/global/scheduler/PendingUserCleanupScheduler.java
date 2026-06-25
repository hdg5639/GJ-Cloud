package gj.cloud.auth.global.scheduler;

import gj.cloud.auth.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class PendingUserCleanupScheduler {

    private final UserRepository userRepository;

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupExpiredPendingUsers() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(24);
        int count = userRepository.deleteExpiredPendingUsers(threshold);
        if (count > 0) {
            log.info("미인증 계정 {}건 정리 완료 (24시간 초과)", count);
        }
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupDeletedUsers() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(30);
        int count = userRepository.deleteExpiredDeletedUsers(threshold);
        if (count > 0) {
            log.info("탈퇴 계정 {}건 영구 삭제 완료 (30일 초과)", count);
        }
    }
}
