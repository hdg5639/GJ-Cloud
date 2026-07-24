package gj.cloud.user.api.controller;

import gj.cloud.user.application.profile.dto.ProfileSearchResult;
import gj.cloud.user.domain.profile.entity.UserProfileEntity;
import gj.cloud.user.domain.profile.repository.UserProfileRepository;
import gj.cloud.user.domain.sshkey.repository.SshKeyRepository;
import gj.cloud.user.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Hidden
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalProfileController {

    private static final int MIN_QUERY_LENGTH = 2;

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

    // 조직 초대용 사용자 검색 — vm 서비스(InternalVmServiceJwtValidator)만 호출 가능. 전체 유저 디렉터리
    // 마이닝을 막기 위해 2자 미만 쿼리는 빈 목록으로 응답(400으로 막지 않음 — 타이핑 중 매 글자마다
    // 호출되는 프론트 자동완성이라 조용히 무시하는 편이 자연스러움).
    @GetMapping("/profiles/search")
    public ApiResponse<List<ProfileSearchResult>> searchProfiles(@RequestParam String query) {
        String trimmed = query.trim();
        if (trimmed.length() < MIN_QUERY_LENGTH) {
            return ApiResponse.ok(List.of());
        }
        List<ProfileSearchResult> results = profileRepository
                .findTop10ByNicknameContainingIgnoreCaseOrEmailContainingIgnoreCase(trimmed, trimmed)
                .stream()
                .map(ProfileSearchResult::from)
                .toList();
        return ApiResponse.ok(results);
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
