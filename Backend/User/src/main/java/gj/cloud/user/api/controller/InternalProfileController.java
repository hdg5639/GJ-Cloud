package gj.cloud.user.api.controller;

import gj.cloud.user.domain.profile.entity.UserProfileEntity;
import gj.cloud.user.domain.profile.repository.UserProfileRepository;
import gj.cloud.user.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalProfileController {

    private final UserProfileRepository profileRepository;

    @PostMapping("/profiles")
    public ApiResponse<Void> createProfile(@RequestBody ProfileInitRequest request) {
        profileRepository.findById(request.userId()).ifPresentOrElse(
                existing -> {},
                () -> profileRepository.save(UserProfileEntity.createDefault(request.userId(), request.email()))
        );
        return ApiResponse.ok(null);
    }

    public record ProfileInitRequest(String userId, String email) {}
}
