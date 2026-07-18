package gj.cloud.ops.application.deployment.dto;

import gj.cloud.ops.domain.deployment.entity.DeploymentEntity;

import java.time.LocalDateTime;

// 암호화된 compose 원문/resolved 내용은 응답에 포함하지 않음 (필요 시 별도 조회 API로 분리)
public record DeploymentResponse(
        String id,
        String vmId,
        String status,
        String sourceType,
        String sourceRevision,
        String releaseDir,
        String previousDeploymentId,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime deployedAt
) {
    public static DeploymentResponse from(DeploymentEntity entity) {
        return new DeploymentResponse(
                entity.getId(),
                entity.getVmId(),
                entity.getStatus().name(),
                entity.getSourceType().name(),
                entity.getSourceRevision(),
                entity.getReleaseDir(),
                entity.getPreviousDeploymentId(),
                entity.getErrorMessage(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getDeployedAt()
        );
    }
}
