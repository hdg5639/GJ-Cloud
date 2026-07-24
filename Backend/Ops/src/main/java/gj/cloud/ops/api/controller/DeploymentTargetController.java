package gj.cloud.ops.api.controller;

import gj.cloud.ops.application.deployment.dto.DeploymentTargetResponse;
import gj.cloud.ops.application.deployment.dto.DeploymentTargetToggleRequest;
import gj.cloud.ops.application.deployment.dto.DeploymentResponse;
import gj.cloud.ops.application.deployment.dto.RepoConfig;
import gj.cloud.ops.application.deployment.service.DeploymentExecutor;
import gj.cloud.ops.application.deployment.service.DeploymentTargetService;
import gj.cloud.ops.application.github.dto.GithubRepositoryAccess;
import gj.cloud.ops.application.github.service.GithubAppService;
import gj.cloud.ops.application.vmclient.VmServiceClient;
import gj.cloud.ops.domain.deployment.entity.DeploymentEntity;
import gj.cloud.ops.domain.deployment.entity.DeploymentTargetEntity;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import gj.cloud.ops.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/ops/{vmId}/deployment-targets")
@RequiredArgsConstructor
public class DeploymentTargetController {

    private static final String PERMISSION_DEPLOY = "DEPLOY";

    private final DeploymentTargetService targetService;
    private final DeploymentExecutor deploymentExecutor;
    private final GithubAppService githubAppService;
    private final VmServiceClient vmServiceClient;

    @GetMapping
    public ApiResponse<List<DeploymentTargetResponse>> list(
            HttpServletRequest request, @PathVariable UUID vmId
    ) {
        requireDeployPermission(request, vmId);
        return ApiResponse.ok(targetService.list(vmId.toString()));
    }

    @PatchMapping("/{targetId}/auto-deploy")
    public ApiResponse<DeploymentTargetResponse> setAutoDeploy(
            HttpServletRequest request,
            @PathVariable UUID vmId,
            @PathVariable String targetId,
            @Valid @RequestBody DeploymentTargetToggleRequest body
    ) {
        requireDeployPermission(request, vmId);
        return ApiResponse.ok(DeploymentTargetResponse.from(
                targetService.setAutoDeploy(vmId.toString(), targetId, body.enabled())));
    }

    @PostMapping("/{targetId}/redeploy")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<DeploymentResponse> redeploy(
            HttpServletRequest request,
            @PathVariable UUID vmId,
            @PathVariable String targetId
    ) {
        String bearerToken = requireDeployPermission(request, vmId);
        DeploymentTargetEntity target = targetService.findOwned(vmId.toString(), targetId);
        GithubRepositoryAccess githubAccess =
                target.getGithubInstallationId() != null && target.getGithubRepositoryId() != null
                        ? githubAppService.resolveRepositoryAccess(
                                target.getGithubInstallationId(), target.getGithubRepositoryId())
                        : null;
        RepoConfig repoConfig = new RepoConfig(
                githubAccess != null ? githubAccess.cloneUrl() : target.getRepositoryUrl(),
                target.getBranch(),
                githubAccess != null ? githubAccess.token() : null,
                target.getContext(),
                target.getInstallPath());
        DeploymentEntity deployment = deploymentExecutor.enqueueRetryForTarget(
                bearerToken,
                vmId.toString(),
                target,
                repoConfig,
                targetService.restoreArtifact(target));
        return ApiResponse.ok(DeploymentResponse.from(deployment));
    }

    // 완전 삭제 — 컨테이너 중지 + 이 target이 만든 모든 이미지/git 저장소/노출 라우트 정리 후
    // target 레코드를 비활성화한다. 배포 이력(DeploymentEntity)은 감사 목적으로 남는다.
    @DeleteMapping("/{targetId}")
    public ApiResponse<Void> delete(
            HttpServletRequest request, @PathVariable UUID vmId, @PathVariable String targetId
    ) {
        String bearerToken = requireDeployPermission(request, vmId);
        DeploymentTargetEntity target = targetService.findOwned(vmId.toString(), targetId);
        deploymentExecutor.deleteTarget(bearerToken, vmId.toString(), target);
        return ApiResponse.ok();
    }

    private String requireDeployPermission(HttpServletRequest request, UUID vmId) {
        String header = request.getHeader("Authorization");
        String token = header != null && header.startsWith("Bearer ") ? header.substring(7) : header;
        if (!vmServiceClient.getContext(token, vmId.toString()).hasPermission(PERMISSION_DEPLOY)) {
            throw new OpsException(OpsErrorCode.FORBIDDEN);
        }
        return token;
    }
}
