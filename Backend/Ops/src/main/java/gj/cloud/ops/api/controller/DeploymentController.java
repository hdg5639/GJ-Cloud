package gj.cloud.ops.api.controller;

import gj.cloud.ops.application.deployment.ai.AiComposeReviewer;
import gj.cloud.ops.application.deployment.ai.AiGenerationResult;
import gj.cloud.ops.application.deployment.ai.AiSpecGeneratorClient;
import gj.cloud.ops.application.deployment.ai.ComposeReviewFinding;
import gj.cloud.ops.application.deployment.dto.ComposeArtifact;
import gj.cloud.ops.application.deployment.dto.ComposeDetectionRequest;
import gj.cloud.ops.application.deployment.dto.ComposeReviewRequest;
import gj.cloud.ops.application.deployment.dto.ComposeRouterPlanRequest;
import gj.cloud.ops.application.deployment.dto.ComposeSpecResponse;
import gj.cloud.ops.application.deployment.dto.DeploymentCreateRequest;
import gj.cloud.ops.application.deployment.dto.DeploymentFromSpecRequest;
import gj.cloud.ops.application.deployment.dto.DeploymentResponse;
import gj.cloud.ops.application.deployment.dto.DeploymentTeardownRequest;
import gj.cloud.ops.application.deployment.dto.GenerateDeploymentSpecRequest;
import gj.cloud.ops.application.deployment.dto.RepoConfig;
import gj.cloud.ops.application.deployment.repoanalysis.ComposeDetectionResult;
import gj.cloud.ops.application.deployment.repoanalysis.RepositorySnapshotBuilder;
import gj.cloud.ops.application.deployment.routing.ComposeRouterPlanResult;
import gj.cloud.ops.application.deployment.routing.ComposeRouterPlanner;
import gj.cloud.ops.application.deployment.service.DeploymentEventPublisher;
import gj.cloud.ops.application.deployment.service.DeploymentExecutor;
import gj.cloud.ops.application.deployment.service.DeploymentTargetService;
import gj.cloud.ops.application.github.dto.GithubRepositoryAccess;
import gj.cloud.ops.application.github.service.GithubAppService;
import gj.cloud.ops.application.preview.dto.PreviewBlueprintSnapshot;
import gj.cloud.ops.application.deployment.spec.DeploymentSpec;
import gj.cloud.ops.application.deployment.spec.DeploymentSpecPolicyValidator;
import gj.cloud.ops.application.deployment.spec.DeploymentSpecRenderer;
import gj.cloud.ops.application.deployment.spec.DeploymentSpecValidator;
import gj.cloud.ops.application.vmclient.VmServiceClient;
import gj.cloud.ops.application.vmclient.dto.VmContextResponse;
import gj.cloud.ops.domain.deployment.entity.DeploymentEntity;
import gj.cloud.ops.domain.deployment.entity.DeploymentTargetEntity;
import gj.cloud.ops.domain.deployment.enums.SourceType;
import gj.cloud.ops.domain.deployment.repository.DeploymentRepository;
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
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

@Tag(name = "Deployment", description = "D-2 사용자 지정 배포 파이프라인")
@RestController
@RequestMapping("/ops/{vmId}/deployments")
@RequiredArgsConstructor
public class DeploymentController {

    private static final String PERMISSION_DEPLOY = "DEPLOY";

    private final DeploymentExecutor deploymentExecutor;
    private final DeploymentRepository deploymentRepository;
    private final DeploymentEventPublisher eventPublisher;
    private final DeploymentSpecValidator deploymentSpecValidator;
    private final DeploymentSpecPolicyValidator deploymentSpecPolicyValidator;
    private final DeploymentSpecRenderer deploymentSpecRenderer;
    private final AiSpecGeneratorClient aiSpecGeneratorClient;
    private final AiComposeReviewer aiComposeReviewer;
    private final RepositorySnapshotBuilder repositorySnapshotBuilder;
    private final ComposeRouterPlanner composeRouterPlanner;
    private final VmServiceClient vmServiceClient;
    private final DeploymentTargetService deploymentTargetService;
    private final GithubAppService githubAppService;

    @Operation(summary = "배포 생성 (Raw Compose)", description = "체크아웃~라우트 등록까지 비동기로 진행됩니다. 즉시 202를 반환하고 SSE로 진행 상황을 수신하세요.")
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<DeploymentResponse> create(
            HttpServletRequest request,
            @PathVariable UUID vmId,
            @AuthenticationPrincipal OpsPrincipal principal,
            @Valid @RequestBody DeploymentCreateRequest body
    ) {
        String bearerToken = extractToken(request);
        requireDeployPermission(bearerToken, vmId.toString());
        ComposeArtifact artifact = new ComposeArtifact(
                body.composeContent(),
                body.environmentFiles() != null ? body.environmentFiles() : List.of(),
                List.of(),
                body.exposedRoutes() != null ? body.exposedRoutes() : List.of(),
                body.healthChecks() != null ? body.healthChecks() : List.of(),
                SourceType.RAW_COMPOSE
        );
        ResolvedRepository repository = resolveRepository(
                principal.userId(), body.repoUrl(), body.branch(), body.patToken(),
                body.githubInstallationId(), body.githubRepositoryId());
        RepoConfig repoConfig = new RepoConfig(
                repository.repoUrl(), repository.branch(), repository.token(), body.context(), body.installPath());
        DeploymentTargetEntity target = createTargetIfRequested(
                principal, vmId.toString(), body.targetName(), Boolean.TRUE.equals(body.autoDeploy()),
                repository, artifact, body.context(), body.installPath());

        DeploymentEntity deployment;
        try {
            deployment = target == null
                    ? deploymentExecutor.enqueue(bearerToken, vmId.toString(), repoConfig, artifact)
                    : deploymentExecutor.enqueueForTarget(
                            bearerToken, vmId.toString(), target, repoConfig, artifact);
        } catch (RuntimeException e) {
            if (target != null) {
                deploymentTargetService.deactivate(target.getId());
            }
            throw e;
        }
        return ApiResponse.ok(DeploymentResponse.from(deployment));
    }

    @Operation(summary = "배포 생성 (기본 템플릿)", description = "DeploymentSpec을 compose로 렌더링한 뒤 Raw Compose와 동일한 파이프라인(공통 Validator 포함)으로 진행합니다.")
    @PostMapping("/from-spec")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<DeploymentResponse> createFromSpec(
            HttpServletRequest request,
            @PathVariable UUID vmId,
            @AuthenticationPrincipal OpsPrincipal principal,
            @Valid @RequestBody DeploymentFromSpecRequest body
    ) {
        String bearerToken = extractToken(request);
        requireDeployPermission(bearerToken, vmId.toString());
        deploymentSpecValidator.validate(body.spec());
        deploymentSpecPolicyValidator.validate(body.spec());
        ComposeArtifact rendered = deploymentSpecRenderer.render(body.spec());
        ComposeArtifact artifact = new ComposeArtifact(
                rendered.composeContent(), rendered.environmentFiles(), rendered.uploadedFiles(),
                rendered.exposedRoutes(), rendered.healthChecks(), SourceType.AI_SPEC);
        ResolvedRepository repository = resolveRepository(
                principal.userId(), body.repoUrl(), body.branch(), body.patToken(),
                body.githubInstallationId(), body.githubRepositoryId());
        RepoConfig repoConfig = new RepoConfig(
                repository.repoUrl(), repository.branch(), repository.token(), null, body.installPath());
        DeploymentTargetEntity target = createTargetIfRequested(
                principal, vmId.toString(), body.targetName(), Boolean.TRUE.equals(body.autoDeploy()),
                repository, artifact, null, body.installPath());

        DeploymentEntity deployment;
        try {
            deployment = target == null
                    ? deploymentExecutor.enqueue(bearerToken, vmId.toString(), repoConfig, artifact)
                    : deploymentExecutor.enqueueForTarget(
                            bearerToken, vmId.toString(), target, repoConfig, artifact);
        } catch (RuntimeException e) {
            if (target != null) {
                deploymentTargetService.deactivate(target.getId());
            }
            throw e;
        }
        return ApiResponse.ok(DeploymentResponse.from(deployment));
    }

    @Operation(summary = "배포 스펙 AI 자동생성 (D-3)",
            description = "저장소를 결정론적으로 먼저 분석하고, 규칙으로 확정 못한 서비스만 AI에게 넘겨 DeploymentSpec을 생성합니다. "
                    + "status=READY가 아니면(NEEDS_INPUT/UNSUPPORTED/CONFLICT) spec이 없으니 unresolved 사유를 확인하세요. "
                    + "즉시 배포하지 않으며, 검토/수정 후 /from-spec으로 배포하세요.")
    @PostMapping("/ai-spec/generate")
    public ApiResponse<AiGenerationResult> generateSpec(
            HttpServletRequest request,
            @PathVariable UUID vmId,
            @AuthenticationPrincipal OpsPrincipal principal,
            @Valid @RequestBody GenerateDeploymentSpecRequest body
    ) {
        String bearerToken = extractToken(request);
        VmContextResponse context = vmServiceClient.getContext(bearerToken, vmId.toString());
        if (!context.hasPermission(PERMISSION_DEPLOY)) {
            throw new OpsException(OpsErrorCode.FORBIDDEN);
        }
        ResolvedRepository repository = resolveRepository(
                principal.userId(), body.repoUrl(), body.branch(), body.patToken(),
                body.githubInstallationId(), body.githubRepositoryId());
        GenerateDeploymentSpecRequest resolvedRequest = new GenerateDeploymentSpecRequest(
                repository.repoUrl(),
                repository.branch(),
                repository.token(),
                body.services(),
                body.infrastructure(),
                body.existingNetworkName(),
                body.githubInstallationId(),
                body.githubRepositoryId());
        AiGenerationResult result = aiSpecGeneratorClient.generate(vmId.toString(), resolvedRequest);
        return ApiResponse.ok(result);
    }

    @Operation(summary = "배포 스펙 AI 검수 (D.5-1)",
            description = "결정론적 검증(구조+정책)을 통과한 스펙을 실제로 렌더링한 뒤, 그 최종 compose 내용을 비차단 AI 검수합니다. "
                    + "환경변수 비밀값은 AI에게 전송되기 전 redact됩니다. 코멘트만 반환하며 배포를 승인/거부하지 않습니다.")
    @PostMapping("/ai-spec/review")
    public ApiResponse<List<ComposeReviewFinding>> reviewSpec(
            HttpServletRequest request,
            @PathVariable UUID vmId,
            @Valid @RequestBody DeploymentSpec spec
    ) {
        String bearerToken = extractToken(request);
        VmContextResponse context = vmServiceClient.getContext(bearerToken, vmId.toString());
        if (!context.hasPermission(PERMISSION_DEPLOY)) {
            throw new OpsException(OpsErrorCode.FORBIDDEN);
        }
        deploymentSpecValidator.validate(spec);
        deploymentSpecPolicyValidator.validate(spec);
        ComposeArtifact artifact = deploymentSpecRenderer.render(spec);
        List<ComposeReviewFinding> findings = aiComposeReviewer.review(vmId.toString(), artifact.composeContent());
        return ApiResponse.ok(findings);
    }

    @Operation(summary = "저장소 Compose 파일 탐지",
            description = "저장소를 임시로 얕게 클론해 배포 디렉터리 아래의 compose.yaml/yml 또는 "
                    + "docker-compose.yaml/yml을 탐지합니다. 원문은 응답 후 서버에 남기지 않습니다.")
    @PostMapping("/compose/detect")
    public ApiResponse<ComposeDetectionResult> detectCompose(
            HttpServletRequest request,
            @PathVariable UUID vmId,
            @AuthenticationPrincipal OpsPrincipal principal,
            @Valid @RequestBody ComposeDetectionRequest body
    ) {
        String bearerToken = extractToken(request);
        requireDeployPermission(bearerToken, vmId.toString());
        ResolvedRepository repository = resolveRepository(
                principal.userId(), body.repoUrl(), body.branch(), body.patToken(),
                body.githubInstallationId(), body.githubRepositoryId());
        ComposeDetectionResult result = repositorySnapshotBuilder.detectCompose(
                repository.repoUrl(), repository.branch(), repository.token(), body.context());
        return ApiResponse.ok(result);
    }

    @Operation(summary = "Raw Compose AI 검수",
            description = "저장소에서 탐지했거나 사용자가 작성한 Compose 원문을 비차단 AI 검수합니다. "
                    + "비밀값은 AI 전송 전에 마스킹되며, 검수 결과는 배포를 승인하거나 차단하지 않습니다.")
    @PostMapping("/compose/review")
    public ApiResponse<List<ComposeReviewFinding>> reviewCompose(
            HttpServletRequest request,
            @PathVariable UUID vmId,
            @Valid @RequestBody ComposeReviewRequest body
    ) {
        String bearerToken = extractToken(request);
        requireDeployPermission(bearerToken, vmId.toString());
        return ApiResponse.ok(aiComposeReviewer.review(vmId.toString(), body.composeContent()));
    }

    @Operation(summary = "다중 서비스 Compose 라우터 보강안 생성",
            description = "Compose의 애플리케이션 서비스와 내부 포트를 결정론적으로 분석해, "
                    + "기존 Nginx/Caddy/Traefik 계열 라우터가 없을 때 Caddy 단일 진입점 보강안을 만듭니다. "
                    + "포트를 확정할 수 없으면 원문을 변경하지 않고 NEEDS_INPUT으로 반환합니다.")
    @PostMapping("/compose/router/plan")
    public ApiResponse<ComposeRouterPlanResult> planComposeRouter(
            HttpServletRequest request,
            @PathVariable UUID vmId,
            @Valid @RequestBody ComposeRouterPlanRequest body
    ) {
        String bearerToken = extractToken(request);
        requireDeployPermission(bearerToken, vmId.toString());
        return ApiResponse.ok(composeRouterPlanner.plan(
                body.composeContent(), body.routerHostPort(), body.servicePorts()));
    }

    @Operation(summary = "배포 이력 조회")
    @GetMapping
    public ApiResponse<List<DeploymentResponse>> list(HttpServletRequest request, @PathVariable UUID vmId) {
        requireDeployPermission(request, vmId);
        List<DeploymentResponse> responses = deploymentRepository.findAllByVmIdOrderByCreatedAtDesc(vmId.toString())
                .stream()
                .map(DeploymentResponse::from)
                .toList();
        return ApiResponse.ok(responses);
    }

    @Operation(summary = "배포 현재 상태 조회", description = "SSE 연결 전 초기 상태 확인용 REST 엔드포인트")
    @GetMapping("/{deploymentId}")
    public ApiResponse<DeploymentResponse> get(
            HttpServletRequest request, @PathVariable UUID vmId, @PathVariable String deploymentId
    ) {
        requireDeployPermission(request, vmId);
        DeploymentEntity entity = findOwned(vmId, deploymentId);
        return ApiResponse.ok(DeploymentResponse.from(entity));
    }

    @Operation(summary = "배포 compose 스펙 조회 (재시도/수정 후 재배포용)",
            description = "저장된 compose 원문 및 환경변수/라우트/헬스체크 설정을 복호화해 반환합니다. " +
                    "repoUrl/branch/patToken은 저장되지 않으므로 재제출 시 다시 입력해야 합니다.")
    @GetMapping("/{deploymentId}/compose-spec")
    public ApiResponse<ComposeSpecResponse> composeSpec(
            HttpServletRequest request, @PathVariable UUID vmId, @PathVariable String deploymentId
    ) {
        requireDeployPermission(request, vmId);
        DeploymentEntity entity = findOwned(vmId, deploymentId);
        return ApiResponse.ok(deploymentExecutor.getComposeSpec(entity));
    }

    @Operation(summary = "Auto Preview blueprint 스냅샷 조회",
            description = "이 배포가 Auto Preview로 생성됐다면, 배포 시점의 capabilities/pages/authStrategy와 " +
                    "리졸브된 Block 배치를 재분석 없이 그대로 반환합니다. Auto Preview 배포가 아니면 null을 반환합니다.")
    @GetMapping("/{deploymentId}/preview-blueprint")
    public ApiResponse<PreviewBlueprintSnapshot> previewBlueprint(
            HttpServletRequest request, @PathVariable UUID vmId, @PathVariable String deploymentId
    ) {
        requireDeployPermission(request, vmId);
        DeploymentEntity entity = findOwned(vmId, deploymentId);
        return ApiResponse.ok(deploymentExecutor.getPreviewBlueprint(entity));
    }

    @Operation(summary = "특정 성공 배포로 수동 롤백", description = "재빌드 없이 대상 배포 시점의 이미지로 컨테이너만 재기동합니다. 대상은 반드시 SUCCEEDED 상태여야 합니다.")
    @PostMapping("/{deploymentId}/rollback")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<DeploymentResponse> rollback(
            HttpServletRequest request, @PathVariable UUID vmId, @PathVariable String deploymentId
    ) {
        requireDeployPermission(request, vmId);
        String bearerToken = extractToken(request);
        DeploymentEntity target = findOwned(vmId, deploymentId);
        DeploymentEntity rollbackEntity = deploymentExecutor.rollbackTo(bearerToken, vmId.toString(), target);
        return ApiResponse.ok(DeploymentResponse.from(rollbackEntity));
    }

    @Operation(summary = "배포 내리기", description = "현재 활성화된(최신 SUCCEEDED) 배포의 컨테이너를 중지/제거합니다. "
            + "removeRouteNicknames에 닉네임을 넣으면 그 노출 포트(Cloudflare 라우트)도 함께 정리하고, "
            + "비워두면 컨테이너만 내리고 포트는 그대로 둡니다.")
    @PostMapping("/{deploymentId}/teardown")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<DeploymentResponse> teardown(
            HttpServletRequest request, @PathVariable UUID vmId, @PathVariable String deploymentId,
            @RequestBody(required = false) DeploymentTeardownRequest body
    ) {
        requireDeployPermission(request, vmId);
        String bearerToken = extractToken(request);
        DeploymentEntity target = findOwned(vmId, deploymentId);
        List<String> removeRouteNicknames = body != null ? body.removeRouteNicknames() : List.of();
        DeploymentEntity stopping = deploymentExecutor.teardown(bearerToken, vmId.toString(), target, removeRouteNicknames);
        return ApiResponse.ok(DeploymentResponse.from(stopping));
    }

    // 주의: EventSource는 커스텀 Authorization 헤더를 못 붙이지만, 이 엔드포인트는 아직 ?token= 같은
    // 쿼리 파라미터 인증 경로가 없음 — 배포 SSE 프론트엔드를 실제로 붙일 때 별도로 해결해야 하는
    // 기존 갭이며, 이번 IDOR 수정 범위에는 포함하지 않음(다른 3개 엔드포인트와 동일하게 Authorization 헤더로만 검증).
    @Operation(summary = "배포 진행 상황 SSE 스트림", description = "afterSequence로 재연결 시 누락된 이벤트부터 재생합니다.")
    @GetMapping("/{deploymentId}/events")
    public SseEmitter events(
            HttpServletRequest request,
            @PathVariable UUID vmId,
            @PathVariable String deploymentId,
            @RequestParam(defaultValue = "0") long afterSequence
    ) {
        requireDeployPermission(request, vmId);
        findOwned(vmId, deploymentId);
        return eventPublisher.subscribe(deploymentId, afterSequence);
    }

    // OPS-SEC-001: list/get/events는 기존에 vmId 소유 여부만 확인하고 VM 권한 조회를 하지 않아
    // 다른 VM의 deploymentId를 알아내면 조회 가능한 IDOR였음 — 다른 엔드포인트와 동일하게 DEPLOY 권한을 요구하도록 통일
    private void requireDeployPermission(HttpServletRequest request, UUID vmId) {
        String bearerToken = extractToken(request);
        VmContextResponse context = vmServiceClient.getContext(bearerToken, vmId.toString());
        if (!context.hasPermission(PERMISSION_DEPLOY)) {
            throw new OpsException(OpsErrorCode.FORBIDDEN);
        }
    }

    private DeploymentEntity findOwned(UUID vmId, String deploymentId) {
        return deploymentRepository.findById(deploymentId)
                .filter(entity -> entity.getVmId().equals(vmId.toString()))
                .orElseThrow(() -> new OpsException(OpsErrorCode.DEPLOYMENT_NOT_FOUND));
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return header;
    }

    private void requireDeployPermission(String bearerToken, String vmId) {
        if (!vmServiceClient.getContext(bearerToken, vmId).hasPermission(PERMISSION_DEPLOY)) {
            throw new OpsException(OpsErrorCode.FORBIDDEN);
        }
    }

    private DeploymentTargetEntity createTargetIfRequested(
            OpsPrincipal principal,
            String vmId,
            String targetName,
            boolean autoDeploy,
            ResolvedRepository repository,
            ComposeArtifact artifact,
            String context,
            String installPath
    ) {
        if (!StringUtils.hasText(targetName)) {
            if (autoDeploy) {
                throw new OpsException(OpsErrorCode.AUTO_DEPLOY_REQUIRES_GITHUB);
            }
            return null;
        }
        return deploymentTargetService.create(
                vmId,
                principal.userId(),
                principal.email(),
                targetName,
                repository.repoUrl(),
                repository.branch(),
                repository.githubAccess(),
                artifact,
                context,
                installPath,
                autoDeploy
        );
    }

    private ResolvedRepository resolveRepository(
            String userId,
            String repoUrl,
            String branch,
            String patToken,
            Long installationId,
            Long repositoryId
    ) {
        if (installationId == null && repositoryId == null) {
            return new ResolvedRepository(repoUrl, branch, patToken, null);
        }
        if (installationId == null || repositoryId == null) {
            throw new OpsException(OpsErrorCode.INVALID_REPO_CONFIG);
        }
        GithubRepositoryAccess access =
                githubAppService.resolveRepositoryAccess(userId, installationId, repositoryId);
        String resolvedBranch = StringUtils.hasText(branch) ? branch : access.defaultBranch();
        return new ResolvedRepository(access.cloneUrl(), resolvedBranch, access.token(), access);
    }

    private record ResolvedRepository(
            String repoUrl,
            String branch,
            String token,
            GithubRepositoryAccess githubAccess
    ) {
    }
}
