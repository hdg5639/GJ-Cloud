package gj.cloud.auth.api.controller;

import gj.cloud.auth.application.auth.service.AuthService;
import gj.cloud.auth.global.exception.AuthException;
import gj.cloud.auth.global.exception.enums.AuthErrorCode;
import gj.cloud.auth.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// SEC-004: User 서비스가 관리자 정지/복구 액션을 Auth와 동기화하기 위해 호출하는 내부 API.
// Auth가 계정 접근 상태(로그인/토큰 갱신/교환 가능 여부)의 단일 진실 공급원이다.
@Hidden
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserStatusController {

    private final AuthService authService;

    @PatchMapping("/{userId}/status")
    public ApiResponse<Void> updateStatus(@PathVariable String userId, @Valid @RequestBody StatusUpdateRequest request) {
        switch (request.status()) {
            case "SUSPENDED" -> authService.suspendUser(userId);
            case "ACTIVE" -> authService.restoreUser(userId);
            default -> throw new AuthException(AuthErrorCode.INVALID_ACCESS_TOKEN);
        }
        return ApiResponse.ok(null);
    }

    public record StatusUpdateRequest(@Pattern(regexp = "SUSPENDED|ACTIVE") String status) {}
}
