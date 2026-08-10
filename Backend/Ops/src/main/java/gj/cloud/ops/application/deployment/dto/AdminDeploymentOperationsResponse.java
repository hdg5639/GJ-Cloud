package gj.cloud.ops.application.deployment.dto;

import gj.cloud.ops.domain.deployment.entity.DeploymentEntity;
import gj.cloud.ops.domain.deployment.entity.DeploymentOrphanCleanupEntity;
import gj.cloud.ops.domain.deployment.entity.DeploymentTargetEntity;

import java.time.LocalDateTime;
import java.util.List;

public record AdminDeploymentOperationsResponse(
        Summary summary,
        List<Target> targets,
        List<Deployment> recentDeployments,
        List<CleanupEvent> cleanupEvents
) {
    public record Summary(int totalTargets, int activeTargets, int activeAutoDeployments,
                          int orphanedTargets, int recentFailedDeployments) {}

    public record Target(
            String id, String vmId, String ownerUserId, String ownerEmail, String name,
            String repository, String branch, String lifecycleStatus, boolean autoDeployEnabled,
            String latestRequestedRevision, String latestDeployedRevision,
            String orphanReason, LocalDateTime orphanedAt, LocalDateTime updatedAt
    ) {
        public static Target from(DeploymentTargetEntity target) {
            String lifecycle = target.getOrphanedAt() != null ? "ORPHANED" : target.isActive() ? "ACTIVE" : "INACTIVE";
            String repository = target.getGithubRepositoryFullName() != null
                    ? target.getGithubRepositoryFullName() : target.getRepositoryUrl();
            return new Target(target.getId(), target.getVmId(), target.getOwnerUserId(), target.getOwnerEmail(),
                    target.getName(), repository, target.getBranch(), lifecycle, target.isAutoDeployEnabled(),
                    target.getLatestRequestedRevision(), target.getLatestDeployedRevision(), target.getOrphanReason(),
                    target.getOrphanedAt(), target.getUpdatedAt());
        }
    }

    public record Deployment(
            String id, String vmId, String deploymentTargetId, String triggerType, String status,
            String sourceType, String revision, String errorMessage, String lastEvent,
            LocalDateTime createdAt, LocalDateTime deployedAt
    ) {
        public static Deployment from(DeploymentEntity deployment, String lastEvent) {
            return new Deployment(deployment.getId(), deployment.getVmId(), deployment.getDeploymentTargetId(),
                    deployment.getTriggerType().name(), deployment.getStatus().name(), deployment.getSourceType().name(),
                    deployment.getSourceRevision(), deployment.getErrorMessage(), lastEvent,
                    deployment.getCreatedAt(), deployment.getDeployedAt());
        }
    }

    public record CleanupEvent(
            String id, String deploymentTargetId, String vmId, String ownerUserId, String ownerEmail,
            String targetName, String action, String reason, boolean hadRelatedData, LocalDateTime createdAt
    ) {
        public static CleanupEvent from(DeploymentOrphanCleanupEntity event) {
            return new CleanupEvent(event.getId(), event.getDeploymentTargetId(), event.getVmId(),
                    event.getOwnerUserId(), event.getOwnerEmail(), event.getTargetName(), event.getAction(),
                    event.getReason(), event.isHadRelatedData(), event.getCreatedAt());
        }
    }
}
