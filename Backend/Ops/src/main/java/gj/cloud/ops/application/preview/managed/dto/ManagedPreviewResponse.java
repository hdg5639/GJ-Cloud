package gj.cloud.ops.application.preview.managed.dto;

import gj.cloud.ops.domain.preview.entity.ManagedPreviewDeploymentEntity;
import java.time.LocalDateTime;

// 일반 사용자 응답에는 workerId, VMID, node, internal IP, SSH 키 참조를 절대 포함하지 않는다.
public record ManagedPreviewResponse(String id, String targetType, String status, String url,
        String deploymentId, LocalDateTime createdAt, LocalDateTime deployedAt, LocalDateTime expiresAt,
        String errorMessage) {
    public static ManagedPreviewResponse from(ManagedPreviewDeploymentEntity e) {
        String url = e.getHostname().contains(".") ? "https://" + e.getHostname() : null;
        return new ManagedPreviewResponse(e.getId(), "MANAGED", e.getStatus().name(), url, e.getDeploymentId(),
                e.getCreatedAt(), e.getDeployedAt(), e.getExpiresAt(), e.getErrorMessage());
    }
}
