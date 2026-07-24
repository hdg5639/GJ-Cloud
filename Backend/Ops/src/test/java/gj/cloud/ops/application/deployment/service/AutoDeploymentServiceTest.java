package gj.cloud.ops.application.deployment.service;

import gj.cloud.ops.application.github.service.GithubAppService;
import gj.cloud.ops.application.vmclient.VmAutomationClient;
import gj.cloud.ops.domain.deployment.entity.DeploymentEntity;
import gj.cloud.ops.domain.deployment.entity.DeploymentTargetEntity;
import gj.cloud.ops.domain.deployment.enums.DeploymentStatus;
import gj.cloud.ops.domain.deployment.enums.DeploymentTriggerType;
import gj.cloud.ops.domain.deployment.enums.SourceType;
import gj.cloud.ops.domain.deployment.repository.DeploymentRepository;
import gj.cloud.ops.domain.deployment.repository.DeploymentTargetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AutoDeploymentServiceTest {

    private final DeploymentTargetRepository targetRepository = mock(DeploymentTargetRepository.class);
    private final DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
    private final DeploymentTargetService targetService = mock(DeploymentTargetService.class);
    private final DeploymentExecutor deploymentExecutor = mock(DeploymentExecutor.class);
    private final DeploymentLockService lockService = mock(DeploymentLockService.class);
    private final GithubAppService githubAppService = mock(GithubAppService.class);
    private final VmAutomationClient vmAutomationClient = mock(VmAutomationClient.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final TaskExecutor taskExecutor = mock(TaskExecutor.class);

    private AutoDeploymentService service;

    @BeforeEach
    void setUp() {
        service = new AutoDeploymentService(
                targetRepository,
                deploymentRepository,
                targetService,
                deploymentExecutor,
                lockService,
                githubAppService,
                vmAutomationClient,
                redisTemplate,
                taskExecutor);
        when(deploymentRepository.findAllByStatus(DeploymentStatus.STOPPING))
                .thenReturn(List.of());
        when(deploymentRepository.findAllByTriggerTypeAndStatusNotIn(
                org.mockito.ArgumentMatchers.eq(DeploymentTriggerType.GIT_PUSH),
                org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of());
    }

    @Test
    void restoresTheRollbackTargetAsTheActiveDeploymentAfterRestart() {
        DeploymentTargetEntity target = target("target-1", "newer");
        DeploymentEntity newer = deployment("newer", "target-1", DeploymentStatus.SUCCEEDED, null);
        DeploymentEntity older = deployment("older", "target-1", DeploymentStatus.SUCCEEDED, null);
        DeploymentEntity rollback = deployment(
                "rollback", "target-1", DeploymentStatus.ROLLED_BACK, "older");

        when(targetRepository.findAll()).thenReturn(List.of(target));
        when(deploymentRepository.findById("newer")).thenReturn(Optional.of(newer));
        when(deploymentRepository.findById("older")).thenReturn(Optional.of(older));
        when(deploymentRepository.findTopByDeploymentTargetIdOrderByCreatedAtDesc("target-1"))
                .thenReturn(Optional.of(rollback));

        service.recoverInterruptedAutomaticDeployments();

        verify(targetService).markActiveDeployment("target-1", "older", "older-sha");
    }

    @Test
    void clearsAnActivePointerWhoseDeploymentWasStopped() {
        DeploymentTargetEntity target = target("target-1", "stopped");
        DeploymentEntity stopped = deployment(
                "stopped", "target-1", DeploymentStatus.STOPPED, null);
        DeploymentEntity failed = deployment(
                "failed", "target-1", DeploymentStatus.FAILED, "stopped");

        when(targetRepository.findAll()).thenReturn(List.of(target));
        when(deploymentRepository.findById("stopped")).thenReturn(Optional.of(stopped));
        when(deploymentRepository.findTopByDeploymentTargetIdOrderByCreatedAtDesc("target-1"))
                .thenReturn(Optional.of(failed));

        service.recoverInterruptedAutomaticDeployments();

        verify(targetService).clearActiveDeployment("target-1", "stopped");
    }

    @Test
    void doesNotRescheduleAWebhookRequestThatAlreadyCreatedADeployment() {
        LocalDateTime requestedAt = LocalDateTime.now();
        String revision = "0123456789abcdef0123456789abcdef01234567";
        DeploymentTargetEntity target = DeploymentTargetEntity.builder()
                .id("target-1")
                .active(true)
                .autoDeployEnabled(true)
                .latestRequestedRevision(revision)
                .latestRequestedAt(requestedAt)
                .createdAt(requestedAt)
                .updatedAt(requestedAt)
                .build();
        when(targetRepository.findAll()).thenReturn(List.of(target));
        when(redisTemplate.hasKey("auto-deploy-pending:target-1")).thenReturn(false);
        when(deploymentRepository
                .existsByDeploymentTargetIdAndRequestedRevisionAndCreatedAtGreaterThanEqual(
                        "target-1", revision, requestedAt))
                .thenReturn(true);

        service.retryPendingDeployments();

        verify(taskExecutor, never()).execute(org.mockito.ArgumentMatchers.any(Runnable.class));
    }

    private DeploymentTargetEntity target(String id, String latestDeploymentId) {
        LocalDateTime now = LocalDateTime.now();
        return DeploymentTargetEntity.builder()
                .id(id)
                .active(true)
                .latestDeploymentId(latestDeploymentId)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private DeploymentEntity deployment(
            String id,
            String targetId,
            DeploymentStatus status,
            String previousDeploymentId
    ) {
        LocalDateTime now = LocalDateTime.now();
        return DeploymentEntity.builder()
                .id(id)
                .vmId("vm-1")
                .deploymentTargetId(targetId)
                .status(status)
                .sourceType(SourceType.RAW_COMPOSE)
                .sourceRevision(id + "-sha")
                .previousDeploymentId(previousDeploymentId)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
