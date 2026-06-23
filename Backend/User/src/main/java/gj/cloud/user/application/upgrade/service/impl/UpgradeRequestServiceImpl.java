package gj.cloud.user.application.upgrade.service.impl;

import gj.cloud.user.application.upgrade.dto.CreateUpgradeRequestRequest;
import gj.cloud.user.application.upgrade.dto.ReviewUpgradeRequestRequest;
import gj.cloud.user.application.upgrade.dto.UpgradeRequestResponse;
import gj.cloud.user.application.upgrade.service.UpgradeRequestService;
import gj.cloud.user.domain.plan.enums.PlanType;
import gj.cloud.user.domain.profile.entity.UserProfileEntity;
import gj.cloud.user.domain.profile.repository.UserProfileRepository;
import gj.cloud.user.domain.upgrade.entity.UpgradeRequestEntity;
import gj.cloud.user.domain.upgrade.enums.UpgradeRequestStatus;
import gj.cloud.user.domain.upgrade.enums.UpgradeRequestType;
import gj.cloud.user.domain.upgrade.repository.UpgradeRequestRepository;
import gj.cloud.user.global.exception.UserException;
import gj.cloud.user.global.exception.enums.UserErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpgradeRequestServiceImpl implements UpgradeRequestService {

    private final UpgradeRequestRepository upgradeRequestRepository;
    private final UserProfileRepository userProfileRepository;

    @Override
    @Transactional
    public UpgradeRequestResponse createRequest(String userId, CreateUpgradeRequestRequest request) {
        UserProfileEntity profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));

        if (profile.isSuspended()) {
            throw new UserException(UserErrorCode.USER_NOT_FOUND);
        }

        if (profile.getPlanType().equals(request.targetPlanType())) {
            throw new UserException(UserErrorCode.INVALID_PLAN_TYPE);
        }

        boolean pendingExists = upgradeRequestRepository.findByUserIdAndStatus(userId, UpgradeRequestStatus.PENDING)
                .isPresent();
        if (pendingExists) {
            throw new UserException(UserErrorCode.PENDING_REQUEST_EXISTS);
        }

        UpgradeRequestType type = request.targetPlanType().ordinal() > profile.getPlanType().ordinal()
                ? UpgradeRequestType.UPGRADE
                : UpgradeRequestType.DOWNGRADE;

        UpgradeRequestEntity entity = UpgradeRequestEntity.createRequest(userId, type, request.targetPlanType());
        UpgradeRequestEntity saved = upgradeRequestRepository.save(entity);

        sendRequestNotification(profile.getEmail(), type);

        return UpgradeRequestResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UpgradeRequestResponse> getRequestsByStatus(UpgradeRequestStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size);
        return upgradeRequestRepository.findByStatusOrderByCreatedAtDesc(status, pageable)
                .map(UpgradeRequestResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UpgradeRequestResponse> getUserRequests(String userId) {
        return upgradeRequestRepository.findAllByUserId(userId)
                .stream()
                .map(UpgradeRequestResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public UpgradeRequestResponse reviewRequest(String adminId, UUID requestId, ReviewUpgradeRequestRequest request) {
        UpgradeRequestEntity upgradeRequest = upgradeRequestRepository.findById(requestId)
                .orElseThrow(() -> new UserException(UserErrorCode.REQUEST_NOT_FOUND));

        if (!upgradeRequest.getStatus().equals(UpgradeRequestStatus.PENDING)) {
            throw new UserException(UserErrorCode.INVALID_REQUEST_STATUS);
        }

        UserProfileEntity profile = userProfileRepository.findById(upgradeRequest.getUserId())
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));

        if (request.approved()) {
            upgradeRequest.approve(adminId);
            upgradeRequestRepository.save(upgradeRequest);
            updateUserPlan(upgradeRequest.getUserId(), upgradeRequest.getTargetPlanType());
        } else {
            upgradeRequest.reject(adminId, request.reason());
            upgradeRequestRepository.save(upgradeRequest);
        }

        sendReviewNotification(profile.getEmail(), upgradeRequest, request.approved());

        return UpgradeRequestResponse.from(upgradeRequest);
    }

    private void updateUserPlan(String userId, PlanType planType) {
        UserProfileEntity profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
        profile.updatePlanType(planType);
        userProfileRepository.save(profile);
    }

    private void sendRequestNotification(String email, UpgradeRequestType type) {
        String subject = type == UpgradeRequestType.UPGRADE
                ? "플랜 업그레이드 요청이 접수되었습니다"
                : "플랜 다운그레이드 요청이 접수되었습니다";
        log.info("플랜 변경 요청 알림: email={}, type={}, subject={}", email, type, subject);
    }

    private void sendReviewNotification(String email, UpgradeRequestEntity request, boolean approved) {
        String subject = approved ? "플랜 변경이 승인되었습니다" : "플랜 변경 요청이 거절되었습니다";
        log.info("플랜 변경 검토 알림: email={}, approved={}, subject={}", email, approved, subject);
    }
}
