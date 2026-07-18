package gj.cloud.ops.domain.deployment.entity;

import gj.cloud.ops.domain.deployment.enums.DeploymentStatus;
import gj.cloud.ops.domain.deployment.enums.SourceType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

// D.7 "배포 레코드 저장 항목" 그대로 반영.
// sourceComposeCiphertext/resolvedComposeCiphertext는 이미 AES-256-GCM으로 암호화된 값만 들어옴 (평문 비밀값 DB 잔류 방지, D.7 ★ 참고)
// serviceImageRefsJson은 {serviceName: {imageTag, imageId}} 형태의 JSON 문자열 (서비스 계층에서 직렬화/역직렬화)
@Entity
@Table(name = "deployments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder(toBuilder = true)
@AllArgsConstructor
public class DeploymentEntity {

    @Id
    private String id;

    // MVP 범위에서는 appId = vmId로 취급 (D.7)
    @Column(name = "vm_id", nullable = false)
    private String vmId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeploymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private SourceType sourceType;

    // commit SHA — 메타데이터로만 저장 (이미지 태그는 deploymentId를 사용, D.7 참고)
    @Column(name = "source_revision")
    private String sourceRevision;

    @Column(name = "source_compose_ciphertext", columnDefinition = "TEXT")
    private String sourceComposeCiphertext;

    // build 필드 제거 + 이미지 태그 고정된 버전. 재기동/롤백은 항상 이것만 사용 (재빌드 없음)
    @Column(name = "resolved_compose_ciphertext", columnDefinition = "TEXT")
    private String resolvedComposeCiphertext;

    @Column(name = "service_image_refs_json", columnDefinition = "TEXT")
    private String serviceImageRefsJson;

    @Column(name = "release_dir")
    private String releaseDir;

    @Column(name = "env_version")
    private Integer envVersion;

    @Column(name = "previous_deployment_id")
    private String previousDeploymentId;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deployed_at")
    private LocalDateTime deployedAt;

    public static DeploymentEntity createQueued(String vmId, SourceType sourceType, String sourceComposeCiphertext,
                                                 String previousDeploymentId) {
        LocalDateTime now = LocalDateTime.now();
        return DeploymentEntity.builder()
                .id(UUID.randomUUID().toString())
                .vmId(vmId)
                .status(DeploymentStatus.QUEUED)
                .sourceType(sourceType)
                .sourceComposeCiphertext(sourceComposeCiphertext)
                .previousDeploymentId(previousDeploymentId)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public DeploymentEntity withStatus(DeploymentStatus status) {
        return this.toBuilder().status(status).updatedAt(LocalDateTime.now()).build();
    }

    public DeploymentEntity withSourceRevision(String sourceRevision, String releaseDir) {
        return this.toBuilder()
                .sourceRevision(sourceRevision)
                .releaseDir(releaseDir)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    public DeploymentEntity withResolvedCompose(String resolvedComposeCiphertext, String serviceImageRefsJson) {
        return this.toBuilder()
                .resolvedComposeCiphertext(resolvedComposeCiphertext)
                .serviceImageRefsJson(serviceImageRefsJson)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    public DeploymentEntity withFailed(String errorMessage) {
        return this.toBuilder()
                .status(DeploymentStatus.FAILED)
                .errorMessage(errorMessage)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    public DeploymentEntity withSucceeded() {
        return this.toBuilder()
                .status(DeploymentStatus.SUCCEEDED)
                .deployedAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    public DeploymentEntity withStatus(DeploymentStatus status, String message) {
        return this.toBuilder().status(status).errorMessage(message).updatedAt(LocalDateTime.now()).build();
    }

    public DeploymentEntity withRolledBack() {
        return this.toBuilder().status(DeploymentStatus.ROLLED_BACK).updatedAt(LocalDateTime.now()).build();
    }
}
