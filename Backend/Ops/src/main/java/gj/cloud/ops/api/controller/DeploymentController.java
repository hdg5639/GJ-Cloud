package gj.cloud.ops.api.controller;

import gj.cloud.ops.application.deployment.dto.ComposeArtifact;
import gj.cloud.ops.application.deployment.dto.DeploymentCreateRequest;
import gj.cloud.ops.application.deployment.dto.DeploymentResponse;
import gj.cloud.ops.application.deployment.dto.RepoConfig;
import gj.cloud.ops.application.deployment.service.DeploymentEventPublisher;
import gj.cloud.ops.application.deployment.service.DeploymentExecutor;
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
@RequestMapping("/api/ops/{vmId}/deployments")
@RequiredArgsConstructor
public class DeploymentController {

    private final DeploymentExecutor deploymentExecutor;
    private final DeploymentRepository deploymentRepository;
    private final DeploymentEventPublisher eventPublisher;

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

    @Operation(summary = "배포 이력 조회")
    @GetMapping
    public ApiResponse<List<DeploymentResponse>> list(@PathVariable UUID vmId) {
        List<DeploymentResponse> responses = deploymentRepository.findAllByVmIdOrderByCreatedAtDesc(vmId.toString())
                .stream()
                .map(DeploymentResponse::from)
                .toList();
        return ApiResponse.ok(responses);
    }

    @Operation(summary = "배포 현재 상태 조회", description = "SSE 연결 전 초기 상태 확인용 REST 엔드포인트")
    @GetMapping("/{deploymentId}")
    public ApiResponse<DeploymentResponse> get(@PathVariable UUID vmId, @PathVariable String deploymentId) {
        DeploymentEntity entity = findOwned(vmId, deploymentId);
        return ApiResponse.ok(DeploymentResponse.from(entity));
    }

    @Operation(summary = "배포 진행 상황 SSE 스트림", description = "afterSequence로 재연결 시 누락된 이벤트부터 재생합니다. EventSource는 Authorization 헤더를 못 붙이므로 ?token= 쿼리 파라미터 사용")
    @GetMapping("/{deploymentId}/events")
    public SseEmitter events(
            @PathVariable UUID vmId,
            @PathVariable String deploymentId,
            @RequestParam(defaultValue = "0") long afterSequence
    ) {
        findOwned(vmId, deploymentId);
        return eventPublisher.subscribe(deploymentId, afterSequence);
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
