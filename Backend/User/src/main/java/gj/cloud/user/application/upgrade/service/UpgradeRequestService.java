package gj.cloud.user.application.upgrade.service;

import gj.cloud.user.application.upgrade.dto.CreateUpgradeRequestRequest;
import gj.cloud.user.application.upgrade.dto.ReviewUpgradeRequestRequest;
import gj.cloud.user.application.upgrade.dto.UpgradeRequestResponse;
import gj.cloud.user.domain.upgrade.enums.UpgradeRequestStatus;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.UUID;

public interface UpgradeRequestService {
    UpgradeRequestResponse createRequest(String userId, CreateUpgradeRequestRequest request);
    Page<UpgradeRequestResponse> getRequestsByStatus(UpgradeRequestStatus status, int page, int size);
    List<UpgradeRequestResponse> getUserRequests(String userId);
    UpgradeRequestResponse reviewRequest(String adminId, UUID requestId, ReviewUpgradeRequestRequest request);
}
