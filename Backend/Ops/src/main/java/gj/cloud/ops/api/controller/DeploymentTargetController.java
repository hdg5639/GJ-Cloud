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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;
import java.util.UUID;

@Tag(name = "Deployment Target", description = "지속 배포 대상, 자동 재배포와 수동 CNAME 연결 관리")
@RestController
@RequestMapping("/ops/{vmId}/deployment-targets")
@RequiredArgsConstructor
public class DeploymentTargetController {

    private static final String PERMISSION_DEPLOY = "DEPLOY";

    private final DeploymentTargetService targetService;
    private final DeploymentExecutor deploymentExecutor;
    private final GithubAppService githubAppService;
    private final VmServiceClient vmServiceClient;

    @Operation(summary = "배포 대상 목록 조회", description = "VM에 남아 있는 활성 배포 대상과 최근 배포·자동 재배포 설정을 조회합니다.")
    @GetMapping
    public ApiResponse<List<DeploymentTargetResponse>> list(
            HttpServletRequest request, @PathVariable UUID vmId
    ) {
        requireDeployPermission(request, vmId);
        return ApiResponse.ok(targetService.list(vmId.toString()));
    }

    @Operation(
            summary = "자동 재배포 설정 변경",
            description = "GitHub App 저장소가 연결된 배포 대상의 push 자동 재배포를 활성화하거나 비활성화합니다."
    )
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

    @Operation(
            summary = "수동 CNAME 포트 연결",
            description = "VM의 수동 공개 포트를 배포 대상에 연결해 카드와 라우트 목록에서 함께 관리합니다."
    )
    @PutMapping("/{targetId}/ports/{portId}")
    public ApiResponse<Void> linkManualCname(
            HttpServletRequest request,
            @PathVariable UUID vmId,
            @PathVariable String targetId,
            @PathVariable UUID portId
    ) {
        String bearerToken = requireDeployPermission(request, vmId);
        DeploymentTargetEntity target = targetService.findOwned(vmId.toString(), targetId);
        vmServiceClient.linkManualPortToDeploymentTarget(
                bearerToken, vmId.toString(), portId.toString(), target.getId());
        return ApiResponse.ok();
    }

    @Operation(
            summary = "수동 CNAME 포트 연결 해제",
            description = "배포 대상과 수동 공개 포트의 표시 연결만 해제하며 포트와 CNAME 자체는 삭제하지 않습니다."
    )
    @DeleteMapping("/{targetId}/ports/{portId}")
    public ApiResponse<Void> unlinkManualCname(
            HttpServletRequest request,
            @PathVariable UUID vmId,
            @PathVariable String targetId,
            @PathVariable UUID portId
    ) {
        String bearerToken = requireDeployPermission(request, vmId);
        DeploymentTargetEntity target = targetService.findOwned(vmId.toString(), targetId);
        vmServiceClient.unlinkManualPortFromDeploymentTarget(
                bearerToken, vmId.toString(), portId.toString(), target.getId());
        return ApiResponse.ok();
    }

    @Operation(
            summary = "배포 대상 재배포",
            description = "저장된 저장소·브랜치·Compose 설정으로 새 배포를 큐에 넣고 즉시 202를 반환합니다."
    )
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
    @Operation(
            summary = "배포 대상 삭제",
            description = "컨테이너, 대상 이미지, Git 저장소와 자동 라우트를 정리하고 대상을 비활성화합니다. 배포 감사 이력과 수동 포트 자체는 유지합니다."
    )
    @DeleteMapping("/{targetId}")
    public ApiResponse<Void> delete(
            HttpServletRequest request, @PathVariable UUID vmId, @PathVariable String targetId
    ) {
        String bearerToken = requireDeployPermission(request, vmId);
        DeploymentTargetEntity target = targetService.findOwned(vmId.toString(), targetId);
        // 수동 CNAME은 삭제하지 않고 표시 연결만 해제해 다른 배포 대상에서 다시 사용할 수 있게 한다.
        vmServiceClient.unlinkAllManualPortsFromDeploymentTarget(
                bearerToken, vmId.toString(), target.getId());
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
