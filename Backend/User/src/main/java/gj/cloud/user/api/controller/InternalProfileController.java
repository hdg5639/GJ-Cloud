package gj.cloud.user.api.controller;

import gj.cloud.user.domain.profile.entity.UserProfileEntity;
import gj.cloud.user.domain.profile.repository.UserProfileRepository;
import gj.cloud.user.domain.sshkey.repository.SshKeyRepository;
import gj.cloud.user.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@Hidden
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalProfileController {

    private final UserProfileRepository profileRepository;
    private final SshKeyRepository sshKeyRepository;

    @PostMapping("/profiles")
    public ApiResponse<Void> createProfile(@RequestBody ProfileInitRequest request) {
        profileRepository.findById(request.userId()).ifPresentOrElse(
                existing -> {},
                () -> profileRepository.save(UserProfileEntity.createDefault(request.userId(), request.email()))
        );
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/users/{userId}")
    @Transactional
    public ApiResponse<Void> deleteUser(@PathVariable String userId) {
        sshKeyRepository.deleteAllByUserId(userId);
        profileRepository.deleteById(userId);
        return ApiResponse.ok(null);
    }

    public record ProfileInitRequest(String userId, String email) {}
}
