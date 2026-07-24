package gj.cloud.ops.application.deployment.dto;

import gj.cloud.ops.domain.deployment.entity.DeploymentTargetEntity;

import java.time.LocalDateTime;

public record DeploymentTargetResponse(
        String id,
        String vmId,
        String name,
        String repositoryUrl,
        String repositoryFullName,
        String branch,
        String sourceType,
        boolean autoDeployEnabled,
        String latestRequestedRevision,
        String latestDeployedRevision,
        String latestDeploymentId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static DeploymentTargetResponse from(DeploymentTargetEntity entity) {
        return new DeploymentTargetResponse(
                entity.getId(),
                entity.getVmId(),
                entity.getName(),
                entity.getRepositoryUrl(),
                entity.getGithubRepositoryFullName(),
                entity.getBranch(),
                entity.getSourceType().name(),
                entity.isAutoDeployEnabled(),
                entity.getLatestRequestedRevision(),
                entity.getLatestDeployedRevision(),
                entity.getLatestDeploymentId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
