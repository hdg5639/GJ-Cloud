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

@RestController
@RequestMapping("/ops/github")
@RequiredArgsConstructor
public class GithubController {

    private static final String PERMISSION_DEPLOY = "DEPLOY";

    private final GithubAppService githubAppService;
    private final VmServiceClient vmServiceClient;

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

    @PostMapping("/installations/complete")
    public ApiResponse<GithubInstallationCompleteResponse> completeInstallation(
            @AuthenticationPrincipal OpsPrincipal principal,
            @Valid @RequestBody GithubInstallationCompleteRequest body
    ) {
        return ApiResponse.ok(githubAppService.completeInstallation(
                principal.userId(), body.code(), body.state()));
    }

    @GetMapping("/installations")
    public ApiResponse<List<GithubInstallationResponse>> listInstallations(
            @AuthenticationPrincipal OpsPrincipal principal
    ) {
        return ApiResponse.ok(githubAppService.listInstallations(principal.userId()));
    }

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
