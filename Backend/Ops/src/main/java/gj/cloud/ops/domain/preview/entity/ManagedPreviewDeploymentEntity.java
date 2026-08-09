package gj.cloud.ops.domain.preview.entity;

import gj.cloud.ops.domain.preview.enums.ManagedPreviewStatus;
import gj.cloud.ops.domain.preview.enums.PreviewTargetType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "managed_preview_deployments")
@Getter @Builder(toBuilder = true) @AllArgsConstructor @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ManagedPreviewDeploymentEntity {
    @Id @Column(length = 36) private String id;
    @Column(name = "user_id", nullable = false, length = 64) private String userId;
    @Column(name = "project_id", length = 100) private String projectId;
    @Enumerated(EnumType.STRING) @Column(name = "target_type", nullable = false, length = 20) private PreviewTargetType targetType;
    @Column(name = "worker_id", nullable = false, length = 36) private String workerId;
    @Column(name = "deployment_target_id", length = 36) private String deploymentTargetId;
    @Column(name = "deployment_id", length = 36) private String deploymentId;
    @Column(name = "container_name", nullable = false, length = 80) private String containerName;
    @Column(name = "compose_project_name", nullable = false, length = 80) private String composeProjectName;
    @Column(nullable = false, unique = true, length = 100) private String hostname;
    @Column(nullable = false, unique = true, length = 40) private String subdomain;
    @Column(name = "dns_record_id", length = 128) private String dnsRecordId;
    @Column(name = "internal_port", nullable = false) private int internalPort;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ManagedPreviewStatus status;
    @Column(name = "error_code", length = 80) private String errorCode;
    @Column(name = "error_message", columnDefinition = "TEXT") private String errorMessage;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "deployed_at") private LocalDateTime deployedAt;
    @Column(name = "expires_at", nullable = false) private LocalDateTime expiresAt;
    @Column(name = "stopped_at") private LocalDateTime stoppedAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    public static ManagedPreviewDeploymentEntity allocate(String userId, String workerId, int port, int ttlHours) {
        String id = UUID.randomUUID().toString();
        String shortId = id.replace("-", "").substring(0, 12);
        LocalDateTime now = LocalDateTime.now();
        String subdomain = "preview-" + shortId;
        return builder().id(id).userId(userId).targetType(PreviewTargetType.MANAGED).workerId(workerId)
                .containerName("gamjabox-preview-" + shortId).composeProjectName("gj_preview_" + shortId)
                .hostname(subdomain).subdomain(subdomain).internalPort(port).status(ManagedPreviewStatus.ALLOCATED)
                .createdAt(now).expiresAt(now.plusHours(ttlHours)).updatedAt(now).build();
    }
    public ManagedPreviewDeploymentEntity routed(String hostname, String dnsRecordId) { return toBuilder().hostname(hostname).dnsRecordId(dnsRecordId).updatedAt(LocalDateTime.now()).build(); }
    public ManagedPreviewDeploymentEntity queued(String targetId, String deploymentId) { return toBuilder().deploymentTargetId(targetId).deploymentId(deploymentId).composeProjectName("gj_" + targetId).status(ManagedPreviewStatus.QUEUED).updatedAt(LocalDateTime.now()).build(); }
    public ManagedPreviewDeploymentEntity status(ManagedPreviewStatus status, String error) {
        LocalDateTime now = LocalDateTime.now();
        return toBuilder().status(status).errorMessage(error).deployedAt(status == ManagedPreviewStatus.RUNNING && deployedAt == null ? now : deployedAt)
                .stoppedAt((status == ManagedPreviewStatus.EXPIRED || status == ManagedPreviewStatus.STOPPED) ? now : stoppedAt).updatedAt(now).build();
    }
}
