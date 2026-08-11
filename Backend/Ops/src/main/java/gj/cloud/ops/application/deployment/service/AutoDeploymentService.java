package gj.cloud.ops.application.deployment.service;

import gj.cloud.ops.application.deployment.dto.ComposeArtifact;
import gj.cloud.ops.application.deployment.dto.RepoConfig;
import gj.cloud.ops.application.github.dto.GithubRepositoryAccess;
import gj.cloud.ops.application.github.service.GithubAppService;
import gj.cloud.ops.application.vmclient.VmAutomationClient;
import gj.cloud.ops.application.vmclient.dto.VmContextResponse;
import gj.cloud.ops.domain.deployment.entity.DeploymentTargetEntity;
import gj.cloud.ops.domain.deployment.entity.DeploymentEntity;
import gj.cloud.ops.domain.deployment.enums.DeploymentStatus;
import gj.cloud.ops.domain.deployment.enums.DeploymentTriggerType;
import gj.cloud.ops.domain.deployment.repository.DeploymentRepository;
import gj.cloud.ops.domain.deployment.repository.DeploymentTargetRepository;
import gj.cloud.ops.domain.deployment.repository.PendingAutomaticTarget;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutoDeploymentService {

    private static final String PENDING_KEY_PREFIX = "auto-deploy-pending:";
    private static final Pattern COMMIT_SHA = Pattern.compile("^[0-9a-f]{40}$");
    private static final DefaultRedisScript<Long> COMPARE_AND_DELETE = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    private final DeploymentTargetRepository targetRepository;
    private final DeploymentRepository deploymentRepository;
    private final DeploymentTargetService targetService;
    private final DeploymentExecutor deploymentExecutor;
    private final DeploymentLockService lockService;
    private final GithubAppService githubAppService;
    private final VmAutomationClient vmAutomationClient;
    private final StringRedisTemplate redisTemplate;
    private final TaskExecutor deploymentTaskExecutor;
    private final DeploymentOrphanReconciler orphanReconciler;

    public void request(DeploymentTargetEntity target, String revision) {
        if (!COMMIT_SHA.matcher(revision).matches()) {
            log.warn("자동 배포 요청 SHA 무시: targetId={}, revision={}", target.getId(), revision);
            return;
        }
        targetService.markRequested(target, revision);
        redisTemplate.opsForValue().set(pendingKey(target.getId()), revision);
        submitSchedule(target.getId());
    }

    public void onDeploymentFinished(String targetId) {
        if (redisTemplate.hasKey(pendingKey(targetId))) {
            submitSchedule(targetId);
        }
    }

    @Scheduled(fixedDelayString = "${ops.auto-deployment.retry-delay-ms:30000}")
    public void retryPendingDeployments() {
        // 비활성 대상은 암호화된 compose 등 큰 엔티티 전체를 읽지 않고 ID만 조회해 stale pending을 지운다.
        for (String targetId : targetRepository.findDisabledTargetIdsWithRequestedRevision()) {
            redisTemplate.delete(pendingKey(targetId));
        }
        for (PendingAutomaticTarget target : targetRepository.findPendingAutomaticTargets()) {
            if (!COMMIT_SHA.matcher(target.revision()).matches()) {
                continue;
            }
            // Redis에 더 최신 요청이 이미 있으면 덮어쓰지 않고, 재시작으로 키만 유실된 경우에만 복구한다.
            redisTemplate.opsForValue().setIfAbsent(pendingKey(target.id()), target.revision());
            submitSchedule(target.id());
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedAutomaticDeployments() {
        reconcileActiveDeployments();

        // teardown은 기존 SUCCEEDED 이력 자체를 잠시 STOPPING으로 바꾼다. Ops가 중간에 재시작되면
        // 실제 down 실행 여부를 단정할 수 없으므로 SUCCEEDED로 되돌려 사용자가 idempotent한 teardown을
        // 다시 실행할 수 있게 한다. 특히 원래 트리거가 GIT_PUSH였다는 이유로 자동 재배포하면 사용자가
        // 명시적으로 내린 앱을 다시 켜는 결과가 되므로 자동 복구 대상에서 먼저 분리한다.
        for (DeploymentEntity deployment : deploymentRepository.findAllByStatus(DeploymentStatus.STOPPING)) {
            deploymentRepository.save(deployment.withStatus(DeploymentStatus.SUCCEEDED));
            String appId = deployment.getDeploymentTargetId() != null
                    ? deployment.getDeploymentTargetId()
                    : deployment.getVmId();
            lockService.forceUnlock(appId);
            log.warn("중단된 배포 내리기 상태 복구: deploymentId={}, appId={}",
                    deployment.getId(), appId);
        }

        List<DeploymentStatus> terminalStatuses = List.of(
                DeploymentStatus.SUCCEEDED,
                DeploymentStatus.FAILED,
                DeploymentStatus.ROLLED_BACK,
                DeploymentStatus.STOPPING,
                DeploymentStatus.STOPPED);
        for (DeploymentEntity deployment : deploymentRepository
                .findAllByTriggerTypeAndStatusNotIn(
                        DeploymentTriggerType.GIT_PUSH, terminalStatuses)) {
            deploymentRepository.save(deployment.withFailed(
                    "Ops 재시작으로 자동 배포가 중단되어 최신 커밋으로 다시 예약했습니다."));
            String targetId = deployment.getDeploymentTargetId();
            if (targetId == null) {
                continue;
            }
            lockService.forceUnlock(targetId);
            targetRepository.findById(targetId)
                    .filter(DeploymentTargetEntity::isActive)
                    .filter(DeploymentTargetEntity::isAutoDeployEnabled)
                    .filter(target -> deployment.getRequestedRevision() != null
                            && COMMIT_SHA.matcher(deployment.getRequestedRevision()).matches())
                    .ifPresent(target -> {
                        String latestRequested = target.getLatestRequestedRevision();
                        String revision = latestRequested != null
                                && COMMIT_SHA.matcher(latestRequested).matches()
                                ? latestRequested
                                : deployment.getRequestedRevision();
                        redisTemplate.opsForValue().set(
                                pendingKey(targetId), revision);
                        submitSchedule(targetId);
                    });
        }
    }

    private void reconcileActiveDeployments() {
        targetRepository.findStoppedActiveDeploymentPointers().forEach(pointer ->
                targetService.clearActiveDeployment(pointer.getTargetId(), pointer.getDeploymentId()));

        List<DeploymentEntity> latestDeployments =
                deploymentRepository.findLatestDeploymentsNeedingActivePointerSync();
        latestDeployments.stream()
                .filter(deployment -> deployment.getStatus() == DeploymentStatus.SUCCEEDED)
                .forEach(deployment -> targetService.markActiveDeployment(
                        deployment.getDeploymentTargetId(),
                        deployment.getId(),
                        deployment.getSourceRevision()));

        Map<String, DeploymentEntity> rollbackTargets = latestDeployments.stream()
                .filter(deployment -> deployment.getStatus() == DeploymentStatus.ROLLED_BACK)
                .filter(deployment -> deployment.getPreviousDeploymentId() != null)
                .collect(java.util.stream.Collectors.toMap(
                        DeploymentEntity::getPreviousDeploymentId,
                        deployment -> deployment,
                        (left, ignored) -> left));
        if (rollbackTargets.isEmpty()) {
            return;
        }
        deploymentRepository.findAllById(rollbackTargets.keySet()).stream()
                .filter(previous -> previous.getStatus() == DeploymentStatus.SUCCEEDED)
                .forEach(previous -> {
                    DeploymentEntity rollback = rollbackTargets.get(previous.getId());
                    targetService.markActiveDeployment(
                            rollback.getDeploymentTargetId(),
                            previous.getId(),
                            previous.getSourceRevision());
                });
    }

    private void submitSchedule(String targetId) {
        try {
            deploymentTaskExecutor.execute(() -> schedule(targetId));
        } catch (RuntimeException e) {
            log.warn("자동 배포 스케줄 제출 실패: targetId={}, error={}", targetId, e.getMessage());
        }
    }

    private void schedule(String targetId) {
        Optional<DeploymentTargetEntity> targetOptional = targetRepository.findById(targetId);
        if (targetOptional.isEmpty()) {
            redisTemplate.delete(pendingKey(targetId));
            return;
        }
        DeploymentTargetEntity target = targetOptional.get();
        if (!target.isActive() || !target.isAutoDeployEnabled()) {
            redisTemplate.delete(pendingKey(targetId));
            return;
        }
        String revision = redisTemplate.opsForValue().get(pendingKey(targetId));
        if (revision == null) {
            return;
        }
        if (isTargetBusy(targetId)) {
            return;
        }

        try {
            // 꺼진 VM을 기다리는 동안 30초마다 GitHub installation token을 새로 발급하지 않도록
            // VM 실행 상태를 먼저 확인한다. enqueue 시점에도 TOCTOU 방지를 위해 다시 검증한다.
            VmContextResponse vmContext = vmAutomationClient.getContext(
                    target.getVmId(), target.getOwnerUserId(), target.getOwnerEmail());
            if (vmContext.internalIp() == null || !"RUNNING".equals(vmContext.status())) {
                return;
            }
            GithubRepositoryAccess access = githubAppService.resolveRepositoryAccess(
                    target.getGithubInstallationId(), target.getGithubRepositoryId());
            ComposeArtifact artifact = targetService.restoreArtifact(target);
            RepoConfig repoConfig = new RepoConfig(
                    access.cloneUrl(), target.getBranch(), access.token(),
                    target.getContext(), target.getInstallPath());
            if (deploymentExecutor.enqueueAutomatic(target, repoConfig, artifact, revision).isPresent()) {
                redisTemplate.execute(COMPARE_AND_DELETE, List.of(pendingKey(targetId)), revision);
            }
        } catch (OpsException e) {
            if (e.getErrorCode() == OpsErrorCode.VM_NOT_FOUND) {
                try {
                    orphanReconciler.handleConfirmedMissingVm(targetId);
                } catch (Exception cleanupError) {
                    log.warn("자동 배포 고아 대상 정리 보류: targetId={}, error={}",
                            targetId, cleanupError.getMessage());
                }
                return;
            }
            log.warn("자동 배포 시작 보류: targetId={}, revision={}, error={}",
                    targetId, revision, e.getMessage());
        } catch (Exception e) {
            // VM이 꺼져 있거나 GitHub가 일시 장애인 경우 pending을 유지한다. 정기 재시도에서 다시 확인하며,
            // 실제 파이프라인이 시작된 뒤의 빌드 실패는 enqueue 직후 pending이 지워지므로 무한 재시도하지 않는다.
            log.warn("자동 배포 시작 보류: targetId={}, revision={}, error={}",
                    targetId, revision, e.getMessage());
        }
    }

    private boolean isTargetBusy(String targetId) {
        // 여기서 stale 여부를 판정해 강제 해제하면 teardown이 락을 잡은 직후 STOPPING 저장 전의
        // 아주 짧은 구간을 stale로 오인할 수 있다. 자동 배포는 pending을 유지하면 되므로 현재
        // 홀더가 있으면 기다리고, 명시적 재시작 복구 또는 TTL/실행기 경로가 락을 정리하게 둔다.
        return lockService.currentHolder(targetId).isPresent();
    }

    private String pendingKey(String targetId) {
        return PENDING_KEY_PREFIX + targetId;
    }
}
