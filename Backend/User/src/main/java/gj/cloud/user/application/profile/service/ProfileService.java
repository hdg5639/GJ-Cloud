package gj.cloud.user.application.profile.service;

import gj.cloud.user.application.profile.dto.ProfileResponse;
import gj.cloud.user.application.profile.dto.ProfileUpdateRequest;
import org.springframework.web.multipart.MultipartFile;

public interface ProfileService {
    ProfileResponse getProfile(String userId, String email);
    ProfileResponse updateProfile(String userId, String email, ProfileUpdateRequest request);
    ProfileResponse updateProfileImage(String userId, String email, MultipartFile file);
    String getPlanType(String userId);
}
