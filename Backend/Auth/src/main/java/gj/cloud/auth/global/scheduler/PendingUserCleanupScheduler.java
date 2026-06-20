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
        int deletedCount = userRepository.deleteExpiredPendingUsers(threshold);
        if (deletedCount > 0) {
            log.info("미인증 계정 {}건 정리 완료 (24시간 초과)", deletedCount);
        }
    }
}
