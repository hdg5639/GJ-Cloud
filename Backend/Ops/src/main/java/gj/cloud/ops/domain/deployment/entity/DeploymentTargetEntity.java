package gj.cloud.ops.domain.deployment.entity;

import gj.cloud.ops.domain.deployment.enums.SourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "deployment_targets")
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeploymentTargetEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "vm_id", nullable = false, length = 36)
    private String vmId;

    @Column(name = "owner_user_id", nullable = false)
    private String ownerUserId;

    @Column(name = "owner_email", nullable = false)
    private String ownerEmail;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(name = "repository_url", nullable = false, length = 500)
    private String repositoryUrl;

    @Column(nullable = false, length = 255)
    private String branch;

    @Column(name = "github_installation_id")
    private Long githubInstallationId;

    @Column(name = "github_repository_id")
    private Long githubRepositoryId;

    @Column(name = "github_repository_full_name", length = 255)
    private String githubRepositoryFullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private SourceType sourceType;

    @Column(name = "source_compose_ciphertext", nullable = false, columnDefinition = "TEXT")
    private String sourceComposeCiphertext;

    @Column(name = "environment_files_ciphertext", columnDefinition = "TEXT")
    private String environmentFilesCiphertext;

    @Column(name = "uploaded_files_ciphertext", columnDefinition = "TEXT")
    private String uploadedFilesCiphertext;

    @Column(name = "exposed_routes_json", columnDefinition = "TEXT")
    private String exposedRoutesJson;

    @Column(name = "health_checks_json", columnDefinition = "TEXT")
    private String healthChecksJson;

    @Column(name = "context")
    private String context;

    @Column(name = "install_path", length = 500)
    private String installPath;

    @Column(name = "auto_deploy_enabled", nullable = false)
    private boolean autoDeployEnabled;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "latest_requested_revision", length = 64)
    private String latestRequestedRevision;

    @Column(name = "latest_requested_at")
    private LocalDateTime latestRequestedAt;

    @Column(name = "latest_deployed_revision", length = 64)
    private String latestDeployedRevision;

    @Column(name = "latest_deployment_id", length = 36)
    private String latestDeploymentId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static DeploymentTargetEntity create(
            String vmId,
            String ownerUserId,
            String ownerEmail,
            String name,
            String repositoryUrl,
            String branch,
            Long githubInstallationId,
            Long githubRepositoryId,
            String githubRepositoryFullName,
            SourceType sourceType,
            String sourceComposeCiphertext,
            String environmentFilesCiphertext,
            String uploadedFilesCiphertext,
            String exposedRoutesJson,
            String healthChecksJson,
            String context,
            String installPath,
            boolean autoDeployEnabled
    ) {
        LocalDateTime now = LocalDateTime.now();
        return DeploymentTargetEntity.builder()
                .id(UUID.randomUUID().toString())
                .vmId(vmId)
                .ownerUserId(ownerUserId)
                .ownerEmail(ownerEmail)
                .name(name)
                .repositoryUrl(repositoryUrl)
                .branch(branch)
                .githubInstallationId(githubInstallationId)
                .githubRepositoryId(githubRepositoryId)
                .githubRepositoryFullName(githubRepositoryFullName)
                .sourceType(sourceType)
                .sourceComposeCiphertext(sourceComposeCiphertext)
                .environmentFilesCiphertext(environmentFilesCiphertext)
                .uploadedFilesCiphertext(uploadedFilesCiphertext)
                .exposedRoutesJson(exposedRoutesJson)
                .healthChecksJson(healthChecksJson)
                .context(context)
                .installPath(installPath)
                .autoDeployEnabled(autoDeployEnabled)
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

}
