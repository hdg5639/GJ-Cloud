package gj.cloud.ops.application.deployment.service;

import gj.cloud.ops.domain.deployment.entity.DeploymentOrphanCleanupEntity;
import gj.cloud.ops.domain.deployment.repository.DeploymentOrphanCleanupRepository;
import gj.cloud.ops.domain.deployment.repository.DeploymentRepository;
import gj.cloud.ops.domain.deployment.repository.DeploymentTargetRepository;
import gj.cloud.ops.domain.preview.repository.ManagedPreviewDeploymentRepository;
import gj.cloud.ops.domain.preview.repository.RegressionSuiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeploymentOrphanMutationService {
    private static final String PENDING_KEY_PREFIX = "auto-deploy-pending:";

    public enum CleanupAction { HARD_DELETED, QUARANTINED, SKIPPED }

    private final DeploymentTargetRepository targetRepository;
    private final DeploymentRepository deploymentRepository;
    private final DeploymentOrphanCleanupRepository cleanupRepository;
    private final ManagedPreviewDeploymentRepository managedPreviewRepository;
    private final RegressionSuiteRepository regressionSuiteRepository;
    private final DeploymentLockService lockService;
    private final StringRedisTemplate redisTemplate;

    @Transactional
    public CleanupAction handleMissingVm(String targetId, String reason) {
        var targetOptional = targetRepository.findByIdForUpdate(targetId);
        if (targetOptional.isEmpty()) {
            redisTemplate.delete(pendingKey(targetId));
            return CleanupAction.SKIPPED;
        }
        var target = targetOptional.get();
        if (!target.isActive()) {
            redisTemplate.delete(pendingKey(targetId));
            return CleanupAction.SKIPPED;
        }

        long deploymentCount = deploymentRepository.countByDeploymentTargetId(targetId);
        boolean managedPreviewPresent = managedPreviewRepository.existsByDeploymentTargetId(targetId);
        boolean regressionSuitePresent = regressionSuiteRepository.existsByDeploymentTargetId(targetId);
        boolean lockPresent = lockService.currentHolder(targetId).isPresent();
        boolean hadRelatedData = deploymentCount > 0 || managedPreviewPresent || regressionSuitePresent;
        CleanupAction action;

        if (!hadRelatedData && !lockPresent) {
            cleanupRepository.save(DeploymentOrphanCleanupEntity.create(
                    target, CleanupAction.HARD_DELETED.name(), reason, false));
            targetRepository.delete(target);
            action = CleanupAction.HARD_DELETED;
        } else {
            targetRepository.quarantineOrphan(targetId, reason, LocalDateTime.now());
            cleanupRepository.save(DeploymentOrphanCleanupEntity.create(
                    target, CleanupAction.QUARANTINED.name(), reason, hadRelatedData));
            action = CleanupAction.QUARANTINED;
        }

        redisTemplate.delete(pendingKey(targetId));
        log.warn("AUDIT action=DEPLOYMENT_TARGET_ORPHAN_CLEANUP targetId={} vmId={} cleanup={} historyCount={} managedPreview={} regressionSuite={} lockPresent={} reason={}",
                targetId, target.getVmId(), action, deploymentCount, managedPreviewPresent,
                regressionSuitePresent, lockPresent, reason);
        return action;
    }

    private String pendingKey(String targetId) {
        return PENDING_KEY_PREFIX + targetId;
    }
}
