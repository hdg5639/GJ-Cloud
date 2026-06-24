package gj.cloud.user.application.profile.service;

import gj.cloud.user.application.profile.dto.ProfileResponse;
import gj.cloud.user.application.profile.dto.ProfileUpdateRequest;

public interface ProfileService {
    ProfileResponse getProfile(String userId, String email);
    ProfileResponse updateProfile(String userId, String email, ProfileUpdateRequest request);
    String getPlanType(String userId);
}
