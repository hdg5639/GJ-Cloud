package gj.cloud.ops.domain.preview.entity;

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
@Table(name = "regression_suites")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegressionSuiteEntity {

    @Id
    private String id;

    @Column(name = "service_id", nullable = false)
    private String serviceId;

    @Column(name = "owner_id", nullable = false)
    private String ownerId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "api_docs_url", nullable = false, length = 2048)
    private String apiDocsUrl;

    @Column(name = "api_base_url", nullable = false, length = 2048)
    private String apiBaseUrl;

    @Column(name = "scenario_ids_json", nullable = false, columnDefinition = "TEXT")
    private String scenarioIdsJson;

    @Column(name = "deployment_target_id", length = 36)
    private String deploymentTargetId;

    @Column(name = "run_on_deployment", nullable = false)
    private boolean runOnDeployment;

    @Column(name = "allow_state_changing_on_deployment", nullable = false)
    private boolean allowStateChangingOnDeployment;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static RegressionSuiteEntity create(
            String serviceId,
            String ownerId,
            String name,
            String description,
            String apiDocsUrl,
            String apiBaseUrl,
            String scenarioIdsJson,
            String deploymentTargetId,
            boolean runOnDeployment,
            boolean allowStateChangingOnDeployment
    ) {
        LocalDateTime now = LocalDateTime.now();
        return RegressionSuiteEntity.builder()
                .id(UUID.randomUUID().toString())
                .serviceId(serviceId)
                .ownerId(ownerId)
                .name(name)
                .description(description)
                .apiDocsUrl(apiDocsUrl)
                .apiBaseUrl(apiBaseUrl)
                .scenarioIdsJson(scenarioIdsJson)
                .deploymentTargetId(deploymentTargetId)
                .runOnDeployment(runOnDeployment)
                .allowStateChangingOnDeployment(allowStateChangingOnDeployment)
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public void deactivate() {
        active = false;
        updatedAt = LocalDateTime.now();
    }
}
