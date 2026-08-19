package gj.cloud.ops.application.deployment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.Session;
import gj.cloud.ops.application.deployment.dto.DeploymentRoutesRequest;
import gj.cloud.ops.application.deployment.dto.DeploymentCommandLogPayload;
import gj.cloud.ops.application.deployment.dto.ExposedRoute;
import gj.cloud.ops.application.deployment.git.GitReleaseManager;
import gj.cloud.ops.domain.deployment.entity.DeploymentEntity;
import gj.cloud.ops.domain.deployment.enums.DeploymentEventType;
import gj.cloud.ops.domain.deployment.enums.DeploymentStatus;
import gj.cloud.ops.domain.deployment.repository.DeploymentRepository;
import gj.cloud.ops.global.ssh.CommandResult;
import gj.cloud.ops.global.ssh.SshCommandExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;

// D.7 롤백 플로우 — SWAPPING 이후 실패(컨테이너 교체/헬스체크) 시 직전 "성공" 배포로 되돌림.
// 재빌드 없음: 직전 배포의 resolved-compose.yml(build 지시문 없음, 이미지 태그 고정)을 그대로 재사용.
@Slf4j
@Component
@RequiredArgsConstructor
public class RollbackService {

    private static final long DOCKER_COMPOSE_TIMEOUT_MS = 300_000;
    private static final String RESOLVED_COMPOSE_FILE_NAME = "resolved-compose.yml";

    private final DeploymentRepository deploymentRepository;
    private final DeploymentEventPublisher eventPublisher;
    private final GitReleaseManager gitReleaseManager;
    private final SshCommandExecutor sshCommandExecutor;
    private final ObjectMapper objectMapper;

    public void rollback(
            Session session,
            String deploymentId,
            String appId,
            Consumer<DeploymentRoutesRequest> routeSynchronizer,
            String reason
    ) {
        eventPublisher.publish(deploymentId, DeploymentEventType.ERROR, reason + " — 롤백을 시작합니다.");
        updateEntity(deploymentId, e -> e.withStatus(DeploymentStatus.ROLLING_BACK, reason));

        DeploymentEntity current = deploymentRepository.findById(deploymentId).orElse(null);
        String previousDeploymentId = current != null ? current.getPreviousDeploymentId() : null;
        if (previousDeploymentId == null) {
            fail(deploymentId, "롤백할 이전 성공 배포가 없습니다: " + reason);
            return;
        }

        DeploymentEntity previous = deploymentRepository.findById(previousDeploymentId).orElse(null);
        if (previous == null || previous.getReleaseDir() == null) {
            fail(deploymentId, "이전 배포의 release 디렉토리를 찾을 수 없습니다: " + reason);
            return;
        }

        String resolvedFilePath = previous.getReleaseDir() + "/" + RESOLVED_COMPOSE_FILE_NAME;
        eventPublisher.publish(deploymentId, DeploymentEventType.STAGE_CHANGE,
                "이전 성공 배포 컨테이너 복구 시작 (deployment: " + previousDeploymentId + ")");
        long startedAt = System.nanoTime();
        CommandResult upResult = sshCommandExecutor.exec(session,
                "docker compose -p gj_" + appId + " -f '" + resolvedFilePath + "' up -d", DOCKER_COMPOSE_TIMEOUT_MS);
        long durationMs = Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
        eventPublisher.publish(deploymentId, DeploymentEventType.BUILD_LOG,
                "롤백 Docker Compose up " + (upResult.isSuccess() ? "성공" : "실패")
                        + " (exit=" + upResult.exitStatus() + ", " + durationMs + "ms)",
                DeploymentCommandLogPayload.from(
                        "rollback-compose-up", "gj_" + appId, upResult, durationMs));
        if (!upResult.isSuccess()) {
            fail(deploymentId, "롤백 중 컨테이너 기동 실패 (exit=" + upResult.exitStatus() + "): "
                    + DeploymentCommandLogPayload.failureSummary(upResult));
            return;
        }

        gitReleaseManager.updateCurrentSymlink(session, appId, previousDeploymentId);

        try {
            routeSynchronizer.accept(new DeploymentRoutesRequest(
                    appId, previousDeploymentId, readRoutes(previous.getExposedRoutesJson())));
        } catch (Exception e) {
            log.warn("롤백 후 라우트 재동기화 실패(무시): {}", e.getMessage());
        }

        updateEntity(deploymentId, DeploymentEntity::withRolledBack);
        eventPublisher.publish(deploymentId, DeploymentEventType.DONE, "롤백 완료 (이전 배포로 복구됨)");
    }

    private void fail(String deploymentId, String message) {
        updateEntity(deploymentId, e -> e.withFailed(message));
        eventPublisher.publish(deploymentId, DeploymentEventType.ERROR, message);
    }

    private void updateEntity(String deploymentId, java.util.function.UnaryOperator<DeploymentEntity> mutator) {
        deploymentRepository.findById(deploymentId).ifPresent(entity -> deploymentRepository.save(mutator.apply(entity)));
    }

    private List<ExposedRoute> readRoutes(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readerForListOf(ExposedRoute.class).readValue(json);
        } catch (Exception e) {
            log.warn("롤백 라우트 스펙 복원 실패(빈 라우트로 진행): {}", e.getMessage());
            return List.of();
        }
    }

}
