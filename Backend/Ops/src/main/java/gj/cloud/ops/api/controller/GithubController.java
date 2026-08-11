package gj.cloud.ops.api.controller;

import gj.cloud.ops.application.github.dto.GithubInstallUrlResponse;
import gj.cloud.ops.application.github.dto.GithubInstallationCompleteRequest;
import gj.cloud.ops.application.github.dto.GithubInstallationCompleteResponse;
import gj.cloud.ops.application.github.dto.GithubInstallationResponse;
import gj.cloud.ops.application.github.dto.GithubRepositoryResponse;
import gj.cloud.ops.application.github.service.GithubAppService;
import gj.cloud.ops.application.vmclient.VmServiceClient;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import gj.cloud.ops.global.response.ApiResponse;
import gj.cloud.ops.global.security.OpsPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "GitHub App", description = "GitHub App 설치와 배포 저장소 연결")
@RestController
@RequestMapping("/ops/github")
@RequiredArgsConstructor
public class GithubController {

    private static final String PERMISSION_DEPLOY = "DEPLOY";

    private final GithubAppService githubAppService;
    private final VmServiceClient vmServiceClient;

    @Operation(
            summary = "GitHub App 설치 URL 생성",
            description = "VM의 DEPLOY 권한을 확인한 뒤 사용자와 VM에 묶인 10분 유효 state를 만들고 GitHub App 설치 URL을 반환합니다."
    )
    @PostMapping("/install-url")
    public ApiResponse<GithubInstallUrlResponse> createInstallUrl(
            HttpServletRequest request,
            @AuthenticationPrincipal OpsPrincipal principal,
            @RequestParam String vmId
    ) {
        String bearerToken = extractToken(request);
        if (!vmServiceClient.getContext(bearerToken, vmId).hasPermission(PERMISSION_DEPLOY)) {
            throw new OpsException(OpsErrorCode.FORBIDDEN);
        }
        return ApiResponse.ok(githubAppService.createInstallUrl(principal.userId(), vmId));
    }

    @Operation(
            summary = "GitHub App 설치 완료",
            description = "GitHub callback의 code와 일회용 state를 검증해 접근 가능한 installation을 저장하고 원래 VM ID를 반환합니다."
    )
    @PostMapping("/installations/complete")
    public ApiResponse<GithubInstallationCompleteResponse> completeInstallation(
            @AuthenticationPrincipal OpsPrincipal principal,
            @Valid @RequestBody GithubInstallationCompleteRequest body
    ) {
        return ApiResponse.ok(githubAppService.completeInstallation(
                principal.userId(), body.code(), body.state()));
    }

    @Operation(summary = "GitHub App 설치 목록 조회", description = "현재 사용자에게 연결된 GitHub App installation 목록을 반환합니다.")
    @GetMapping("/installations")
    public ApiResponse<List<GithubInstallationResponse>> listInstallations(
            @AuthenticationPrincipal OpsPrincipal principal
    ) {
        return ApiResponse.ok(githubAppService.listInstallations(principal.userId()));
    }

    @Operation(
            summary = "접근 가능한 GitHub 저장소 조회",
            description = "사용자의 각 installation에 단기 토큰을 발급해 접근 가능한 저장소를 조회합니다. 삭제된 installation 연결은 정리합니다."
    )
    @GetMapping("/repositories")
    public ApiResponse<List<GithubRepositoryResponse>> listRepositories(
            @AuthenticationPrincipal OpsPrincipal principal
    ) {
        return ApiResponse.ok(githubAppService.listRepositories(principal.userId()));
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return header != null && header.startsWith("Bearer ") ? header.substring(7) : header;
    }
}
