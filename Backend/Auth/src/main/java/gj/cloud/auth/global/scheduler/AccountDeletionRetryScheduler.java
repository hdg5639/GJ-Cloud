package gj.cloud.auth.global.scheduler;

import gj.cloud.auth.application.deletion.service.AccountDeletionAttemptService;
import gj.cloud.auth.domain.deletion.entity.AccountDeletionJobEntity;
import gj.cloud.auth.domain.deletion.enums.AccountDeletionJobStatus;
import gj.cloud.auth.domain.deletion.repository.AccountDeletionJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// REL-001: withdraw() 시점의 즉시 시도가 실패한 계정 삭제 job을 재시도. 최대 시도 횟수를 넘기면
// FAILED_MANUAL_REVIEW로 고정해 무한 재시도를 막고(AccountDeletionJobEntity.recordFailure), 운영자가
// 별도로 확인해야 함을 표시한다.
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountDeletionRetryScheduler {

    private final AccountDeletionJobRepository accountDeletionJobRepository;
    private final AccountDeletionAttemptService accountDeletionAttemptService;

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    @Transactional
    public void retryFailedJobs() {
        List<AccountDeletionJobEntity> jobs =
                accountDeletionJobRepository.findTop100ByStatusOrderByUpdatedAtAsc(
                        AccountDeletionJobStatus.FAILED_RETRYABLE);
        if (jobs.isEmpty()) {
            return;
        }
        log.info("계정 삭제 재시도 대상 {}건", jobs.size());
        for (AccountDeletionJobEntity job : jobs) {
            accountDeletionAttemptService.attempt(job);
        }
        accountDeletionJobRepository.saveAll(jobs);
    }
}
