package gj.cloud.user.api.controller.spec;

import gj.cloud.user.application.usage.dto.UsageResponse;
import gj.cloud.user.global.response.ApiResponse;
import gj.cloud.user.global.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "Usage", description = "VM 사용량 조회 API")
@RequestMapping("/users/usage")
public interface UsageApi {

    @Operation(summary = "현재 플랜 및 VM 사용량 조회")
    @GetMapping
    ApiResponse<UsageResponse> getUsage(@AuthenticationPrincipal UserPrincipal principal, HttpServletRequest request);
}
