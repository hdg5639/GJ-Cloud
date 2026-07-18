package gj.cloud.ops.api.controller;

import gj.cloud.ops.application.deployment.ai.AiComposeReviewer;
import gj.cloud.ops.application.deployment.ai.AiSpecGeneratorClient;
import gj.cloud.ops.application.deployment.dto.ComposeArtifact;
import gj.cloud.ops.application.deployment.dto.DeploymentCreateRequest;
import gj.cloud.ops.application.deployment.dto.DeploymentFromSpecRequest;
import gj.cloud.ops.application.deployment.dto.DeploymentResponse;
import gj.cloud.ops.application.deployment.dto.GenerateDeploymentSpecRequest;
import gj.cloud.ops.application.deployment.dto.RepoConfig;
import gj.cloud.ops.application.deployment.service.DeploymentEventPublisher;
import gj.cloud.ops.application.deployment.service.DeploymentExecutor;
import gj.cloud.ops.application.deployment.spec.DeploymentSpec;
import gj.cloud.ops.application.deployment.spec.DeploymentSpecRenderer;
import gj.cloud.ops.application.deployment.spec.DeploymentSpecValidator;
import gj.cloud.ops.application.vmclient.VmServiceClient;
import gj.cloud.ops.application.vmclient.dto.VmContextResponse;
import gj.cloud.ops.domain.deployment.entity.DeploymentEntity;
import gj.cloud.ops.domain.deployment.enums.SourceType;
import gj.cloud.ops.domain.deployment.repository.DeploymentRepository;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import gj.cloud.ops.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
    private final DeploymentSpecRenderer deploymentSpecRenderer;
    private final AiSpecGeneratorClient aiSpecGeneratorClient;
    private final AiComposeReviewer aiComposeReviewer;
    private final VmServiceClient vmServiceClient;

    @Operation(summary = "배포 생성 (Raw Compose)", description = "체크아웃~라우트 등록까지 비동기로 진행됩니다. 즉시 202를 반환하고 SSE로 진행 상황을 수신하세요.")
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<DeploymentResponse> create(
            HttpServletRequest request,
            @PathVariable UUID vmId,
            @Valid @RequestBody DeploymentCreateRequest body
    ) {
        String bearerToken = extractToken(request);
        ComposeArtifact artifact = new ComposeArtifact(
                body.composeContent(),
                body.environmentFiles() != null ? body.environmentFiles() : List.of(),
                List.of(),
                body.exposedRoutes() != null ? body.exposedRoutes() : List.of(),
                body.healthChecks() != null ? body.healthChecks() : List.of(),
                SourceType.RAW_COMPOSE
        );
        RepoConfig repoConfig = new RepoConfig(body.repoUrl(), body.branch(), body.patToken());

        DeploymentEntity deployment = deploymentExecutor.enqueue(bearerToken, vmId.toString(), repoConfig, artifact);
        return ApiResponse.ok(DeploymentResponse.from(deployment));
    }

    @Operation(summary = "배포 생성 (기본 템플릿)", description = "DeploymentSpec을 compose로 렌더링한 뒤 Raw Compose와 동일한 파이프라인(공통 Validator 포함)으로 진행합니다.")
    @PostMapping("/from-spec")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<DeploymentResponse> createFromSpec(
            HttpServletRequest request,
            @PathVariable UUID vmId,
            @Valid @RequestBody DeploymentFromSpecRequest body
    ) {
        String bearerToken = extractToken(request);
        deploymentSpecValidator.validate(body.spec());
        ComposeArtifact artifact = deploymentSpecRenderer.render(body.spec());
        RepoConfig repoConfig = new RepoConfig(body.repoUrl(), body.branch(), body.patToken());

        DeploymentEntity deployment = deploymentExecutor.enqueue(bearerToken, vmId.toString(), repoConfig, artifact);
        return ApiResponse.ok(DeploymentResponse.from(deployment));
    }

    @Operation(summary = "배포 스펙 AI 자동생성 (D-3)", description = "서비스 카드를 기반으로 AI가 DeploymentSpec을 생성합니다. 즉시 배포하지 않으며, 검토/수정 후 /from-spec으로 배포하세요.")
    @PostMapping("/ai-spec/generate")
    public ApiResponse<DeploymentSpec> generateSpec(
            HttpServletRequest request,
            @PathVariable UUID vmId,
            @Valid @RequestBody GenerateDeploymentSpecRequest body
    ) {
        String bearerToken = extractToken(request);
        VmContextResponse context = vmServiceClient.getContext(bearerToken, vmId.toString());
        if (!context.hasPermission(PERMISSION_DEPLOY)) {
            throw new OpsException(OpsErrorCode.FORBIDDEN);
        }
        DeploymentSpec spec = aiSpecGeneratorClient.generate(vmId.toString(), body);
        return ApiResponse.ok(spec);
    }

    @Operation(summary = "배포 스펙 AI 검수 (D.5-1)", description = "결정론적 검증을 통과한 스펙에 대해서만 비차단 AI 검수를 요청합니다. 코멘트만 반환하며 배포를 승인/거부하지 않습니다.")
    @PostMapping("/ai-spec/review")
    public ApiResponse<List<String>> reviewSpec(
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
        List<String> comments = aiComposeReviewer.review(vmId.toString(), spec);
        return ApiResponse.ok(comments);
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
}
