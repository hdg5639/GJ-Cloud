package gj.cloud.user.application.profile.service.impl;

import gj.cloud.user.application.profile.dto.ProfileResponse;
import gj.cloud.user.application.profile.dto.ProfileUpdateRequest;
import gj.cloud.user.application.profile.service.ProfileService;
import gj.cloud.user.domain.profile.entity.UserProfileEntity;
import gj.cloud.user.domain.profile.repository.UserProfileRepository;
import gj.cloud.user.global.storage.ProfileImageStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private final UserProfileRepository profileRepository;
    private final ProfileImageStorage profileImageStorage;

    @Override
    @Transactional
    public ProfileResponse getProfile(String userId, String email) {
        UserProfileEntity profile = profileRepository.findById(userId)
                .orElseGet(() -> profileRepository.save(UserProfileEntity.createDefault(userId, email)));
        return ProfileResponse.from(profile);
    }

    @Override
    @Transactional(readOnly = true)
    public String getPlanType(String userId) {
        return profileRepository.findById(userId)
                .map(p -> p.getPlanType().name())
                .orElse("FREE");
    }

    @Override
    @Transactional
    public ProfileResponse updateProfile(String userId, String email, ProfileUpdateRequest request) {
        UserProfileEntity profile = profileRepository.findById(userId)
                .orElseGet(() -> profileRepository.save(UserProfileEntity.createDefault(userId, email)));
        // 요청에 없는 필드는 기존 값을 유지 — 예전엔 무조건 덮어써서, 닉네임만 보내는 PATCH(설정 페이지)가
        // profileImageUrl을 매번 null로 지워버리는 문제가 있었음(이미지 필드를 항상 같이 안 보내는 호출부 존재).
        String nickname = request.nickname() != null ? request.nickname() : profile.getNickname();
        String profileImageUrl = request.profileImageUrl() != null ? request.profileImageUrl() : profile.getProfileImageUrl();
        profile.updateProfile(nickname, profileImageUrl);
        return ProfileResponse.from(profile);
    }

    @Override
    @Transactional
    public ProfileResponse updateProfileImage(String userId, String email, MultipartFile file) {
        UserProfileEntity profile = profileRepository.findById(userId)
                .orElseGet(() -> profileRepository.save(UserProfileEntity.createDefault(userId, email)));
        String previousUrl = profile.getProfileImageUrl();
        String newUrl = profileImageStorage.store(file);
        profile.updateProfile(profile.getNickname(), newUrl);
        if (previousUrl != null) {
            profileImageStorage.deleteByUrl(previousUrl);
        }
        return ProfileResponse.from(profile);
    }
}
