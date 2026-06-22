package gj.cloud.user.application.admin.service.impl;

import gj.cloud.user.application.admin.dto.AdminUserResponse;
import gj.cloud.user.application.admin.service.AdminUserService;
import gj.cloud.user.domain.profile.repository.UserProfileRepository;
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

    @Override
    @Transactional
    public AdminUserResponse suspendUser(String userId) {
        var profile = profileRepository.findById(userId)
                .orElseThrow(() -> new UserException(UserErrorCode.PROFILE_NOT_FOUND));
        profile.suspend();
        return AdminUserResponse.from(profileRepository.save(profile));
    }

    @Override
    @Transactional
    public AdminUserResponse activateUser(String userId) {
        var profile = profileRepository.findById(userId)
                .orElseThrow(() -> new UserException(UserErrorCode.PROFILE_NOT_FOUND));
        profile.activate();
        return AdminUserResponse.from(profileRepository.save(profile));
    }
}
