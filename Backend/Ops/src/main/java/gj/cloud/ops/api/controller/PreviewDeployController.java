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
import gj.cloud.ops.application.preview.flow.FlowBlueprintIds;
import gj.cloud.ops.application.preview.flow.RuleBasedFlowGenerator;
import gj.cloud.ops.application.preview.planning.model.PagePlan;
import gj.cloud.ops.application.preview.planning.model.PagePlanMapper;
import gj.cloud.ops.application.preview.planning.patch.PagePlanPatchValidator;
import gj.cloud.ops.application.preview.planning.patch.PlanPatchState;
import gj.cloud.ops.application.preview.service.PreviewBlueprintService;
import gj.cloud.ops.application.preview.managed.ManagedPreviewService;
import gj.cloud.ops.application.preview.managed.dto.ManagedPreviewResponse;
import gj.cloud.ops.domain.preview.entity.ManagedPreviewDeploymentEntity;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.CompiledScenario;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.CompilationStatus;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.PreviewMode;
import gj.cloud.ops.application.preview.scenario.ScenarioValidator;
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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Tag(name = "Preview", description = "Auto Preview — 생성된 소스를 VM에 배포")
@RestController
@RequestMapping("/ops")
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
    private final ManagedPreviewService managedPreviewService;

    @Operation(summary = "Auto Preview 사용자 VM 배포", description = "검증된 Product Blueprint를 Vite 프로젝트로 생성해 선택한 사용자 VM의 새 배포 대상으로 배포합니다.")
    @PostMapping("/{vmId}/preview/deploy")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<DeploymentResponse> deployToVm(
            HttpServletRequest request,
            @PathVariable UUID vmId,
            @AuthenticationPrincipal OpsPrincipal principal,
            @Valid @RequestBody PreviewDeployRequest body
    ) {
        return ApiResponse.ok(deploy(request, vmId, principal, body).userVm());
    }

    @Operation(summary = "Auto Preview 관리형 배포", description = "검증된 Product Blueprint를 Vite 프로젝트로 생성해 GamjaBox 관리형 Worker에 배포합니다.")
    @PostMapping("/preview/deploy")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<ManagedPreviewResponse> deployManaged(
            HttpServletRequest request,
            @AuthenticationPrincipal OpsPrincipal principal,
            @Valid @RequestBody PreviewDeployRequest body
    ) {
        return ApiResponse.ok(deploy(request, null, principal, body).managed());
    }

    private PreviewDeploymentResult deploy(
            HttpServletRequest request,
            UUID vmId,
            OpsPrincipal principal,
            PreviewDeployRequest body
    ) {
        boolean managed = vmId == null;
        String bearerToken = managed ? bearerToken(request) : requireDeployPermission(request, vmId);

        List<PagePlan> pagePlans = body.pagePlans() == null || body.pagePlans().isEmpty()
                ? PagePlanMapper.from(body.pages(), body.capabilities())
                : body.pagePlans();

        // LIST/DETAIL이 없어 유효한 페이지를 만들 수 없는 리소스(PageDraftGenerator가 건너뜀)의
        // capability는 어떤 pagePlan에도 배치되지 않는다. validateFinal이 orphan capability를 거부하므로,
        // 실제로 페이지에 배치된 capability만 배포 대상으로 남긴다 — 렌더 불가한 엔드포인트(업로드/변환 등
        // CREATE·COMMAND만 있는 것) 때문에 배포 전체가 막히지 않도록 안전하게 부분집합만 배포한다(AC-10).
        Set<String> assignedCapabilityIds = pagePlans.stream()
                .flatMap(plan -> plan.capabilityIds().stream())
                .collect(Collectors.toSet());
        List<CompiledScenario> scenarios = body.scenarios() == null ? List.of() : body.scenarios();
        boolean hasRunnableScenario = scenarios.stream()
                .anyMatch(scenario -> scenario.status() != CompilationStatus.UNSUPPORTED);
        PreviewMode previewMode = body.previewMode() == null || !hasRunnableScenario
                ? PreviewMode.OPERATION_PREVIEW : body.previewMode();
        Set<String> availableCapabilityIds = body.capabilities().stream()
                .map(Capability::id)
                .collect(Collectors.toSet());
        List<String> scenarioErrors = scenarios.stream()
                .filter(scenario -> scenario.status() != CompilationStatus.UNSUPPORTED)
                .flatMap(scenario -> ScenarioValidator.validateCompiled(scenario, availableCapabilityIds).stream())
                .toList();
        if (!scenarioErrors.isEmpty()) {
            log.warn("Auto Preview 배포 Scenario 검증 실패: {}", String.join("; ", scenarioErrors));
            throw new OpsException(OpsErrorCode.INVALID_PREVIEW_BLUEPRINT);
        }
        Set<String> scenarioCapabilityIds = scenarios.stream()
                .flatMap(scenario -> scenario.stages().stream())
                .map(stage -> stage.capabilityId())
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());

        List<Capability> pageCapabilities = body.capabilities().stream()
                .filter(capability -> assignedCapabilityIds.contains(capability.id()))
                .toList();
        List<Capability> runtimeCapabilities = body.capabilities().stream()
                .filter(capability -> assignedCapabilityIds.contains(capability.id())
                        || scenarioCapabilityIds.contains(capability.id()))
                .toList();
        if (runtimeCapabilities.size() < body.capabilities().size()) {
            log.warn("Auto Preview 배포: 페이지·시나리오에서 참조되지 않은 capability {}개를 제외함",
                    body.capabilities().size() - runtimeCapabilities.size());
        }

        List<FlowBlueprint> flows;
        List<ApiBinding> bindings;
        if (body.flows() == null || body.bindings() == null) {
            RuleBasedFlowGenerator.ValidatedResult generated =
                    ruleBasedFlowGenerator.generateValidated(pagePlans, pageCapabilities);
            flows = generated.result().flows();
            bindings = generated.result().bindings();
        } else {
            // 이전 분석 결과나 AI Patch가 pageId+action 이름만으로 id를 만들었을 수 있다. Flow 실행
            // 연결은 trigger가 정본이므로 중복 id만 결정적으로 고유화하고 나머지 최종 검증은 그대로 한다.
            flows = FlowBlueprintIds.ensureUnique(body.flows());
            bindings = body.bindings();
        }

        PlanPatchState state = new PlanPatchState(pagePlans, flows, bindings);
        List<String> blueprintErrors = PagePlanPatchValidator.validateFinal(state, pageCapabilities);
        if (!blueprintErrors.isEmpty()) {
            log.warn("Auto Preview 배포 Blueprint 검증 실패: {}", String.join("; ", blueprintErrors));
            throw new OpsException(OpsErrorCode.INVALID_PREVIEW_BLUEPRINT);
        }

        List<PageDraft> effectivePages = PagePlanMapper.toDrafts(pagePlans);
        Map<String, List<Block>> pageBlocks =
                previewBlueprintService.compilePagePlanBlocks(pagePlans, pageCapabilities, body.purpose());
        if (hasErrorFinding(effectivePages, pageBlocks, pageCapabilities)) {
            throw new OpsException(OpsErrorCode.INVALID_PREVIEW_BLUEPRINT);
        }

        // 전면 이전(Phase B): 포털 preview-runtime 실물을 번들하는 빌더로 생성 — 배포 앱이 라이브
        // 프리뷰와 같은 컴포넌트(Blueprint 파츠 포함)를 쓴다. 검증(hasErrorFinding)은 기본 Block으로
        // 수행하고 파츠 치환은 아티팩트 생성 단계에서만 적용해 배포가 막히지 않는다.
        ManagedPreviewDeploymentEntity managedAllocation = managed
                ? managedPreviewService.allocate(bearerToken, principal.userId()) : null;
        ComposeArtifact artifact = managed
                ? previewComposeArtifactBuilder.buildManaged(
                        body.apiBaseUrl(), runtimeCapabilities, effectivePages, flows, bindings,
                        body.authStrategy(), body.purpose(), scenarios, previewMode, body.partOverrides(),
                        managedAllocation.getInternalPort(), managedAllocation.getContainerName())
                : previewComposeArtifactBuilder.build(
                        body.apiBaseUrl(), runtimeCapabilities, effectivePages, flows, bindings,
                        body.authStrategy(), body.purpose(), scenarios, previewMode, body.partOverrides());

        GenerationMode generationMode = body.generationMode() == null
                ? GenerationMode.RULE_BASED : body.generationMode();
        PreviewBlueprintSnapshot snapshot = new PreviewBlueprintSnapshot(
                body.apiBaseUrl(), runtimeCapabilities, effectivePages, body.authStrategy(), pageBlocks,
                RegistryStatus.VALIDATED, body.purpose(), pagePlans, flows, bindings, generationMode,
                BlueprintCompiler.VERSION, ComponentRegistry.VERSION, scenarios, previewMode);

        if (managed) {
            ManagedPreviewResponse response = managedPreviewService.deploy(
                    managedAllocation, principal.email(), body.targetName(), artifact);
            managedPreviewService.findDeployment(response.deploymentId())
                    .ifPresent(deployment -> deploymentExecutor.attachPreviewBlueprint(deployment, snapshot));
            return new PreviewDeploymentResult(null, response);
        }

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

        deployment = deploymentExecutor.attachPreviewBlueprint(deployment, snapshot);
        return new PreviewDeploymentResult(DeploymentResponse.from(deployment), null);
    }

    private record PreviewDeploymentResult(
            DeploymentResponse userVm,
            ManagedPreviewResponse managed
    ) {}

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
        String token = bearerToken(request);
        if (!vmServiceClient.getContext(token, vmId.toString()).hasPermission(PERMISSION_DEPLOY)) {
            throw new OpsException(OpsErrorCode.FORBIDDEN);
        }
        return token;
    }

    private String bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return header != null && header.startsWith("Bearer ") ? header.substring(7) : header;
    }
}
