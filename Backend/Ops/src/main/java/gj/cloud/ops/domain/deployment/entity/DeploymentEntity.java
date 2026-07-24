package gj.cloud.ops.domain.deployment.entity;

import gj.cloud.ops.domain.deployment.enums.DeploymentStatus;
import gj.cloud.ops.domain.deployment.enums.DeploymentTriggerType;
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

    // VM 소유 범위. 런타임 appId는 deploymentTargetId가 있으면 target ID, 레거시 행만 vmId를 사용한다.
    @Column(name = "vm_id", nullable = false)
    private String vmId;

    @Column(name = "deployment_target_id")
    private String deploymentTargetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false)
    @Builder.Default
    private DeploymentTriggerType triggerType = DeploymentTriggerType.MANUAL;

    @Column(name = "requested_revision", length = 64)
    private String requestedRevision;

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

    // 재시도/수정 후 재배포 시 프리필용 — environmentFiles는 비밀값(.env 내용)을 담을 수 있어 compose 원문과
    // 동일하게 암호화, exposedRoutes/healthChecks는 비밀값이 없어 평문 JSON으로 저장 (serviceImageRefsJson과 동일한 관례)
    @Column(name = "environment_files_ciphertext", columnDefinition = "TEXT")
    private String environmentFilesCiphertext;

    @Column(name = "exposed_routes_json", columnDefinition = "TEXT")
    private String exposedRoutesJson;

    @Column(name = "health_checks_json", columnDefinition = "TEXT")
    private String healthChecksJson;

    @Column(name = "release_dir")
    private String releaseDir;

    // Raw Compose 배포 시 사용자가 선택한 저장소 내 서브디렉토리 (release_dir 기준 상대경로) — null/"."이면 저장소 루트
    @Column(name = "context")
    private String context;

    // VM 파일시스템 절대경로 — 지정하면 이 경로가 current를 가리키는 심볼릭 링크가 됨
    @Column(name = "install_path")
    private String installPath;

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
        return createQueued(vmId, sourceType, sourceComposeCiphertext, previousDeploymentId, null, null, null, null, null);
    }

    public static DeploymentEntity createQueued(String vmId, SourceType sourceType, String sourceComposeCiphertext,
                                                 String previousDeploymentId, String environmentFilesCiphertext,
                                                 String exposedRoutesJson, String healthChecksJson) {
        return createQueued(vmId, sourceType, sourceComposeCiphertext, previousDeploymentId,
                environmentFilesCiphertext, exposedRoutesJson, healthChecksJson, null, null);
    }

    public static DeploymentEntity createQueued(String vmId, SourceType sourceType, String sourceComposeCiphertext,
                                                 String previousDeploymentId, String environmentFilesCiphertext,
                                                 String exposedRoutesJson, String healthChecksJson, String context,
                                                 String installPath) {
        LocalDateTime now = LocalDateTime.now();
        return DeploymentEntity.builder()
                .id(UUID.randomUUID().toString())
                .vmId(vmId)
                .status(DeploymentStatus.QUEUED)
                .sourceType(sourceType)
                .sourceComposeCiphertext(sourceComposeCiphertext)
                .previousDeploymentId(previousDeploymentId)
                .environmentFilesCiphertext(environmentFilesCiphertext)
                .exposedRoutesJson(exposedRoutesJson)
                .healthChecksJson(healthChecksJson)
                .context(context)
                .installPath(installPath)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public static DeploymentEntity createQueuedForTarget(
            String vmId,
            String deploymentTargetId,
            DeploymentTriggerType triggerType,
            String requestedRevision,
            SourceType sourceType,
            String sourceComposeCiphertext,
            String previousDeploymentId,
            String environmentFilesCiphertext,
            String exposedRoutesJson,
            String healthChecksJson,
            String context,
            String installPath
    ) {
        LocalDateTime now = LocalDateTime.now();
        return DeploymentEntity.builder()
                .id(UUID.randomUUID().toString())
                .vmId(vmId)
                .deploymentTargetId(deploymentTargetId)
                .triggerType(triggerType)
                .requestedRevision(requestedRevision)
                .status(DeploymentStatus.QUEUED)
                .sourceType(sourceType)
                .sourceComposeCiphertext(sourceComposeCiphertext)
                .previousDeploymentId(previousDeploymentId)
                .environmentFilesCiphertext(environmentFilesCiphertext)
                .exposedRoutesJson(exposedRoutesJson)
                .healthChecksJson(healthChecksJson)
                .context(context)
                .installPath(installPath)
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
