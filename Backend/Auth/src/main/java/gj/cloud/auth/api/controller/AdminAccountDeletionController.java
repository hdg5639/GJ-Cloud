package gj.cloud.auth.api.controller;

import gj.cloud.auth.api.controller.dto.AccountDeletionJobResponse;
import gj.cloud.auth.domain.deletion.enums.AccountDeletionJobStatus;
import gj.cloud.auth.domain.deletion.repository.AccountDeletionJobRepository;
import gj.cloud.auth.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Stream;

// REL-001: 관리자가 실패/재검토가 필요한 계정 삭제 job을 확인할 수 있는 최소 조회 API.
@Hidden
@RestController
@RequestMapping("/admin/account-deletion-jobs")
@RequiredArgsConstructor
public class AdminAccountDeletionController {

    private final AccountDeletionJobRepository accountDeletionJobRepository;

    @GetMapping("/needs-attention")
    public ApiResponse<List<AccountDeletionJobResponse>> needsAttention() {
        List<AccountDeletionJobResponse> jobs = Stream.concat(
                        accountDeletionJobRepository.findAllByStatus(AccountDeletionJobStatus.FAILED_RETRYABLE).stream(),
                        accountDeletionJobRepository.findAllByStatus(AccountDeletionJobStatus.FAILED_MANUAL_REVIEW).stream()
                )
                .map(AccountDeletionJobResponse::from)
                .toList();
        return ApiResponse.ok(jobs);
    }
}
