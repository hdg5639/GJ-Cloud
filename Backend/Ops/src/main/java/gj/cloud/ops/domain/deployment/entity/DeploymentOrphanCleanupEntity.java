package gj.cloud.ops.domain.deployment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "deployment_orphan_cleanup_events")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeploymentOrphanCleanupEntity {
    @Id private String id;
    @Column(name = "deployment_target_id", nullable = false) private String deploymentTargetId;
    @Column(name = "vm_id", nullable = false) private String vmId;
    @Column(name = "owner_user_id", nullable = false) private String ownerUserId;
    @Column(name = "owner_email", nullable = false) private String ownerEmail;
    @Column(name = "target_name", nullable = false) private String targetName;
    @Column(nullable = false) private String action;
    @Column(nullable = false) private String reason;
    @Column(name = "had_related_data", nullable = false) private boolean hadRelatedData;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;

    public static DeploymentOrphanCleanupEntity create(
            DeploymentTargetEntity target, String action, String reason, boolean hadRelatedData) {
        return DeploymentOrphanCleanupEntity.builder()
                .id(UUID.randomUUID().toString())
                .deploymentTargetId(target.getId())
                .vmId(target.getVmId())
                .ownerUserId(target.getOwnerUserId())
                .ownerEmail(target.getOwnerEmail())
                .targetName(target.getName())
                .action(action)
                .reason(reason)
                .hadRelatedData(hadRelatedData)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
