package gj.cloud.user.application.admin.service.impl;

import gj.cloud.user.application.admin.dto.AdminUserResponse;
import gj.cloud.user.application.admin.service.AdminUserService;
import gj.cloud.user.domain.plan.enums.PlanType;
import gj.cloud.user.domain.profile.entity.UserProfileEntity;
import gj.cloud.user.domain.profile.repository.UserProfileRepository;
import gj.cloud.user.global.auth.AuthServiceClient;
import gj.cloud.user.global.exception.UserException;
import gj.cloud.user.global.exception.enums.UserErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;

// OBS-001: DB 감사로그는 Auth에만 있음(계정 상태의 단일 진실 공급원) — User는 구조화된 로그 라인으로만
// 추적성 확보(AUDIT action=... actorId=... target=... result=... reason=... 고정 포맷).
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final UserProfileRepository profileRepository;
    private final AuthServiceClient authServiceClient;

    @Override
    @Transactional(readOnly = true)
    public List<AdminUserResponse> listUsers() {
        return profileRepository.findAll().stream()
                .map(AdminUserResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdminUserResponse> listUsers(int page, int size) {
        int safePage = Math.max(page, 1) - 1;
        int safeSize = Math.max(1, Math.min(size, 100));
        return profileRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(safePage, safeSize))
                .map(AdminUserResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminUserResponse> listUsersByIds(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return profileRepository.findAllByUserIdIn(userIds.stream().distinct().limit(100).toList()).stream()
                .map(AdminUserResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AdminUserResponse getUser(String userId) {
        return profileRepository.findById(userId)
                .map(AdminUserResponse::from)
                .orElseThrow(() -> new UserException(UserErrorCode.PROFILE_NOT_FOUND));
    }

    // SEC-004: User 자신의 프로필 상태뿐 아니라 Auth의 로그인/토큰 갱신 가능 여부도 함께 정지시켜야
    // 실제로 효력이 있다 — 이전에는 Auth 동기화가 없어 정지된 계정도 로그인/갱신이 계속 가능했다.
    // Auth 호출이 실패해도 로컬 정지 자체는 유지한다(완전한 재시도 보장은 REL-001 본작업 범위).
    @Override
    @Transactional
    public AdminUserResponse suspendUser(String userId) {
        var profile = profileRepository.findById(userId)
                .orElseThrow(() -> new UserException(UserErrorCode.PROFILE_NOT_FOUND));
        profile.suspend();
        AdminUserResponse response = AdminUserResponse.from(profileRepository.save(profile));
        authServiceClient.syncStatus(userId, "SUSPENDED");
        log.info("AUDIT action=ACCOUNT_SUSPENDED targetType=USER targetId={} result=SUCCESS", userId);
        return response;
    }

    @Override
    @Transactional
    public AdminUserResponse activateUser(String userId) {
        var profile = profileRepository.findById(userId)
                .orElseThrow(() -> new UserException(UserErrorCode.PROFILE_NOT_FOUND));
        profile.activate();
        AdminUserResponse response = AdminUserResponse.from(profileRepository.save(profile));
        authServiceClient.syncStatus(userId, "ACTIVE");
        log.info("AUDIT action=ACCOUNT_RESTORED targetType=USER targetId={} result=SUCCESS", userId);
        return response;
    }

    @Override
    @Transactional
    public AdminUserResponse updatePlan(String userId, PlanType planType) {
        var profile = profileRepository.findById(userId)
                .orElseThrow(() -> new UserException(UserErrorCode.PROFILE_NOT_FOUND));
        profile.updatePlanType(planType);
        return AdminUserResponse.from(profileRepository.save(profile));
    }
}
