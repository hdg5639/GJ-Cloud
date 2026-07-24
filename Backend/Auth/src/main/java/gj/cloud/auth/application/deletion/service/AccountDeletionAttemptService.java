package gj.cloud.auth.application.deletion.service;

import gj.cloud.auth.domain.deletion.entity.AccountDeletionJobEntity;
import gj.cloud.auth.domain.deletion.enums.AccountDeletionJobStatus;
import gj.cloud.auth.global.client.UserServiceClient;
import gj.cloud.auth.global.client.VmServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// REL-001: 회원 탈퇴 시 User/VM 서비스 데이터 정리를 실제로 시도하고 결과를 job에 반영하는 공용 로직.
// 최초 시도(AuthServiceImpl.withdraw)와 재시도(AccountDeletionRetryScheduler)가 동일 로직을 공유한다.
@Component
@RequiredArgsConstructor
public class AccountDeletionAttemptService {

    private final UserServiceClient userServiceClient;
    private final VmServiceClient vmServiceClient;

    public void attempt(AccountDeletionJobEntity job) {
        if (!job.isUserServiceDone() && userServiceClient.deleteUser(job.getUserId())) {
            job.markUserServiceDone();
        }
        if (!job.isVmServiceDone() && vmServiceClient.deleteUserData(job.getUserId(), job.getEmail())) {
            job.markVmServiceDone();
        }
        if (job.getStatus() != AccountDeletionJobStatus.COMPLETED) {
            job.recordFailure("user_service_done=" + job.isUserServiceDone() + ", vm_service_done=" + job.isVmServiceDone());
        }
    }
}
