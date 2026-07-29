package gj.cloud.ops.domain.preview.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "custom_scenario_revisions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_custom_scenario_revision",
                columnNames = {"scenario_id", "revision"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class CustomScenarioRevisionEntity {

    @Id
    private String id;

    @Column(name = "scenario_id", nullable = false)
    private String scenarioId;

    @Column(nullable = false)
    private int revision;

    @Column(name = "openapi_fingerprint", nullable = false)
    private String openapiFingerprint;

    @Column(name = "compiled_scenario_json", nullable = false, columnDefinition = "TEXT")
    private String compiledScenarioJson;

    @Column(name = "validation_result_json", nullable = false, columnDefinition = "TEXT")
    private String validationResultJson;

    @Column(nullable = false)
    private boolean valid;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static CustomScenarioRevisionEntity create(
            String scenarioId,
            int revision,
            String openapiFingerprint,
            String compiledScenarioJson,
            String validationResultJson,
            boolean valid
    ) {
        return CustomScenarioRevisionEntity.builder()
                .id(UUID.randomUUID().toString())
                .scenarioId(scenarioId)
                .revision(revision)
                .openapiFingerprint(openapiFingerprint)
                .compiledScenarioJson(compiledScenarioJson)
                .validationResultJson(validationResultJson)
                .valid(valid)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
