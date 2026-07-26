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
import gj.cloud.ops.application.preview.analysis.ComponentRegistry;
import gj.cloud.ops.application.preview.analysis.GenerationMode;
import gj.cloud.ops.application.preview.analysis.PageDraft;
import gj.cloud.ops.application.preview.analysis.RegistryStatus;
import gj.cloud.ops.application.preview.binding.ApiBinding;
import gj.cloud.ops.application.preview.blueprint.BlueprintCompiler;
import gj.cloud.ops.application.preview.build.PreviewComposeArtifactBuilder;
import gj.cloud.ops.application.preview.dto.PreviewBlueprintSnapshot;
import gj.cloud.ops.application.preview.dto.PreviewDeployRequest;
import gj.cloud.ops.application.preview.flow.FlowBlueprint;
import gj.cloud.ops.application.preview.flow.RuleBasedFlowGenerator;
import gj.cloud.ops.application.preview.planning.model.PagePlan;
import gj.cloud.ops.application.preview.planning.model.PagePlanMapper;
import gj.cloud.ops.application.preview.planning.patch.PagePlanPatchValidator;
import gj.cloud.ops.application.preview.planning.patch.PlanPatchState;
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
import lombok.extern.slf4j.Slf4j;
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

@Tag(name = "Preview", description = "Auto Preview — 생성된 소스를 VM에 배포")
@RestController
@RequestMapping("/ops/{vmId}/preview")
@RequiredArgsConstructor
@Slf4j
public class PreviewDeployController {

    private static final String PERMISSION_DEPLOY = "DEPLOY";

    private final PreviewComposeArtifactBuilder previewComposeArtifactBuilder;
    private final PreviewBlueprintService previewBlueprintService;
    private final RuleBasedFlowGenerator ruleBasedFlowGenerator;
    private final DeploymentTargetService deploymentTargetService;
    private final DeploymentExecutor deploymentExecutor;
    private final VmServiceClient vmServiceClient;

    @Operation(summary = "Auto Preview 배포", description = "검증된 Product Blueprint를 Vite 프로젝트로 생성해 새 배포 대상으로 배포합니다.")
    @PostMapping("/deploy")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<DeploymentResponse> deploy(
            HttpServletRequest request,
            @PathVariable UUID vmId,
            @AuthenticationPrincipal OpsPrincipal principal,
            @Valid @RequestBody PreviewDeployRequest body
    ) {
        String bearerToken = requireDeployPermission(request, vmId);

        List<PagePlan> pagePlans = body.pagePlans() == null || body.pagePlans().isEmpty()
                ? PagePlanMapper.from(body.pages(), body.capabilities())
                : body.pagePlans();
        List<FlowBlueprint> flows;
        List<ApiBinding> bindings;
        if (body.flows() == null || body.bindings() == null) {
            RuleBasedFlowGenerator.ValidatedResult generated =
                    ruleBasedFlowGenerator.generateValidated(pagePlans, body.capabilities());
            flows = generated.result().flows();
            bindings = generated.result().bindings();
        } else {
            flows = body.flows();
            bindings = body.bindings();
        }

        PlanPatchState state = new PlanPatchState(pagePlans, flows, bindings);
        List<String> blueprintErrors = PagePlanPatchValidator.validateFinal(state, body.capabilities());
        if (!blueprintErrors.isEmpty()) {
            log.warn("Auto Preview 배포 Blueprint 검증 실패: {}", String.join("; ", blueprintErrors));
            throw new OpsException(OpsErrorCode.INVALID_PREVIEW_BLUEPRINT);
        }

        List<PageDraft> effectivePages = PagePlanMapper.toDrafts(pagePlans);
        Map<String, List<Block>> pageBlocks =
                previewBlueprintService.compilePagePlanBlocks(pagePlans, body.capabilities(), body.purpose());
        if (hasErrorFinding(effectivePages, pageBlocks, body.capabilities())) {
            throw new OpsException(OpsErrorCode.INVALID_PREVIEW_BLUEPRINT);
        }

        ComposeArtifact artifact = previewComposeArtifactBuilder.build(
                body.apiBaseUrl(), body.capabilities(), effectivePages, flows, bindings,
                body.authStrategy(), body.purpose());

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

        GenerationMode generationMode = body.generationMode() == null
                ? GenerationMode.RULE_BASED : body.generationMode();
        PreviewBlueprintSnapshot snapshot = new PreviewBlueprintSnapshot(
                body.apiBaseUrl(), body.capabilities(), effectivePages, body.authStrategy(), pageBlocks,
                RegistryStatus.VALIDATED, body.purpose(), pagePlans, flows, bindings, generationMode,
                BlueprintCompiler.VERSION, ComponentRegistry.VERSION);
        deployment = deploymentExecutor.attachPreviewBlueprint(deployment, snapshot);
        return ApiResponse.ok(DeploymentResponse.from(deployment));
    }

    private boolean hasErrorFinding(List<PageDraft> pages, Map<String, List<Block>> pageBlocks,
                                    List<Capability> capabilities) {
        for (PageDraft page : pages) {
            for (CompatibilityFinding finding : CompatibilityValidator.validate(
                    page, pageBlocks.getOrDefault(page.id(), List.of()), capabilities)) {
                if (finding.severity() == CompatibilitySeverity.ERROR) {
                    log.warn("Auto Preview component compatibility 실패 [{}]: {}", page.id(), finding.message());
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
