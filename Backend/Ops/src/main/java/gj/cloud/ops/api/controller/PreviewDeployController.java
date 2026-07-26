package gj.cloud.ops.api.controller;

import gj.cloud.ops.application.deployment.dto.ComposeArtifact;
import gj.cloud.ops.application.deployment.dto.DeploymentResponse;
import gj.cloud.ops.application.deployment.dto.RepoConfig;
import gj.cloud.ops.application.deployment.service.DeploymentExecutor;
import gj.cloud.ops.application.deployment.service.DeploymentTargetService;
import gj.cloud.ops.application.preview.analysis.Block;
import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.CompatibilityFinding;
import gj.cloud.ops.application.preview.analysis.CompatibilitySeverity;
import gj.cloud.ops.application.preview.analysis.CompatibilityValidator;
import gj.cloud.ops.application.preview.analysis.PageDraft;
import gj.cloud.ops.application.preview.analysis.RegistryStatus;
import gj.cloud.ops.application.preview.build.PreviewComposeArtifactBuilder;
import gj.cloud.ops.application.preview.dto.PreviewBlueprintSnapshot;
import gj.cloud.ops.application.preview.dto.PreviewDeployRequest;
import gj.cloud.ops.application.preview.service.PreviewBlueprintService;
import gj.cloud.ops.application.vmclient.VmServiceClient;
import gj.cloud.ops.domain.deployment.entity.DeploymentEntity;
import gj.cloud.ops.domain.deployment.entity.DeploymentTargetEntity;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import gj.cloud.ops.global.response.ApiResponse;
import gj.cloud.ops.global.security.OpsPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

// Auto Preview Phase D — 확정된 capability/페이지 초안을 Vite+React 프로젝트로 생성해 사용자의 기존
// VM에 새 배포 대상으로 추가한다(전용 Preview VM 풀은 만들지 않음, MVP 결정). Git 저장소가 없는
// target이라 repositoryUrl/branch를 빈 문자열로 저장하고, DeploymentTargetService.create()/
// DeploymentExecutor.runPipeline의 "저장소 없음" 분기가 이를 받아 처리한다.
@Tag(name = "Preview", description = "Auto Preview — 생성된 소스를 VM에 배포")
@RestController
@RequestMapping("/ops/{vmId}/preview")
@RequiredArgsConstructor
public class PreviewDeployController {

    private static final String PERMISSION_DEPLOY = "DEPLOY";

    private final PreviewComposeArtifactBuilder previewComposeArtifactBuilder;
    private final PreviewBlueprintService previewBlueprintService;
    private final DeploymentTargetService deploymentTargetService;
    private final DeploymentExecutor deploymentExecutor;
    private final VmServiceClient vmServiceClient;

    @Operation(summary = "Auto Preview 배포", description = "capability/페이지 초안으로 Vite 프로젝트를 생성해 새 배포 대상으로 배포합니다.")
    @PostMapping("/deploy")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<DeploymentResponse> deploy(
            HttpServletRequest request,
            @PathVariable UUID vmId,
            @AuthenticationPrincipal OpsPrincipal principal,
            @Valid @RequestBody PreviewDeployRequest body
    ) {
        String bearerToken = requireDeployPermission(request, vmId);

        ComposeArtifact artifact = previewComposeArtifactBuilder.build(
                body.apiBaseUrl(), body.capabilities(), body.pages(), body.authStrategy(), body.purpose());

        DeploymentTargetEntity target = deploymentTargetService.create(
                vmId.toString(),
                principal.userId(),
                principal.email(),
                body.targetName(),
                "",
                "",
                null,
                artifact,
                null,
                null,
                false
        );

        RepoConfig repoConfig = new RepoConfig(null, null, null, null, null);
        DeploymentEntity deployment = deploymentExecutor.enqueueForTarget(
                bearerToken, vmId.toString(), target, repoConfig, artifact);

        Map<String, List<Block>> pageBlocks =
                previewBlueprintService.compilePageBlocks(body.pages(), body.capabilities(), body.purpose());
        RegistryStatus status = hasErrorFinding(body.pages(), pageBlocks, body.capabilities())
                ? RegistryStatus.DRAFT
                : RegistryStatus.VALIDATED;
        PreviewBlueprintSnapshot snapshot = new PreviewBlueprintSnapshot(
                body.apiBaseUrl(), body.capabilities(), body.pages(), body.authStrategy(), pageBlocks, status,
                body.purpose());
        deployment = deploymentExecutor.attachPreviewBlueprint(deployment, snapshot);

        return ApiResponse.ok(DeploymentResponse.from(deployment));
    }

    // auto-preview-design/09-registry-lifecycle.md §11 MVP 관계 — "Schema·Build 통과 시
    // PROJECT+VALIDATED". Build는 이미 위에서 예외 없이 ComposeArtifact가 만들어진 시점에 통과했고,
    // 여기서는 Contract·Slot·Compatibility Hard Gate(ERROR Finding)만 마저 확인한다.
    private boolean hasErrorFinding(List<PageDraft> pages, Map<String, List<Block>> pageBlocks, List<Capability> capabilities) {
        for (PageDraft page : pages) {
            for (CompatibilityFinding finding : CompatibilityValidator.validate(page, pageBlocks.get(page.id()), capabilities)) {
                if (finding.severity() == CompatibilitySeverity.ERROR) {
                    return true;
                }
            }
        }
        return false;
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
