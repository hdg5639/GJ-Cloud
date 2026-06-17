package gj.cloud.user.application.profile.service.impl;

import gj.cloud.user.application.profile.dto.ProfileResponse;
import gj.cloud.user.application.profile.dto.ProfileUpdateRequest;
import gj.cloud.user.application.profile.service.ProfileService;
import gj.cloud.user.domain.profile.entity.UserProfileEntity;
import gj.cloud.user.domain.profile.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private final UserProfileRepository profileRepository;

    @Override
    @Transactional
    public ProfileResponse getProfile(String userId, String email) {
        UserProfileEntity profile = profileRepository.findById(userId)
                .orElseGet(() -> profileRepository.save(UserProfileEntity.createDefault(userId, email)));
        return ProfileResponse.from(profile);
    }

    @Override
    @Transactional
    public ProfileResponse updateProfile(String userId, String email, ProfileUpdateRequest request) {
        UserProfileEntity profile = profileRepository.findById(userId)
                .orElseGet(() -> profileRepository.save(UserProfileEntity.createDefault(userId, email)));
        profile.updateProfile(request.nickname(), request.profileImageUrl());
        return ProfileResponse.from(profile);
    }
}
