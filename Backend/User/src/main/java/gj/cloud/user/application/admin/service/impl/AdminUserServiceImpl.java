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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
