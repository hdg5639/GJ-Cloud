package gj.cloud.ops.domain.preview.entity;

import gj.cloud.ops.domain.preview.enums.CustomScenarioStatus;
import gj.cloud.ops.domain.preview.enums.CustomScenarioVisibility;
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
@Table(name = "custom_scenarios")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class CustomScenarioEntity {

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

    @Column(name = "natural_language_source", nullable = false, columnDefinition = "TEXT")
    private String naturalLanguageSource;

    @Column(name = "scenario_definition_json", columnDefinition = "TEXT")
    private String scenarioDefinitionJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CustomScenarioStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CustomScenarioVisibility visibility;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static CustomScenarioEntity generating(
            String serviceId,
            String ownerId,
            String name,
            String description,
            String naturalLanguageSource,
            CustomScenarioVisibility visibility
    ) {
        LocalDateTime now = LocalDateTime.now();
        return CustomScenarioEntity.builder()
                .id(UUID.randomUUID().toString())
                .serviceId(serviceId)
                .ownerId(ownerId)
                .name(name)
                .description(description)
                .naturalLanguageSource(naturalLanguageSource)
                .status(CustomScenarioStatus.GENERATING)
                .visibility(visibility == null ? CustomScenarioVisibility.PRIVATE : visibility)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public void markDraft(String definitionJson) {
        scenarioDefinitionJson = definitionJson;
        status = CustomScenarioStatus.DRAFT;
        updatedAt = LocalDateTime.now();
    }

    public void markValidating() {
        status = CustomScenarioStatus.VALIDATING;
        updatedAt = LocalDateTime.now();
    }

    public void markValidated() {
        status = CustomScenarioStatus.VALIDATED;
        updatedAt = LocalDateTime.now();
    }

    public void completeRevalidation(boolean reactivate) {
        status = reactivate ? CustomScenarioStatus.ACTIVE : CustomScenarioStatus.VALIDATED;
        updatedAt = LocalDateTime.now();
    }

    public void activate() {
        status = CustomScenarioStatus.ACTIVE;
        updatedAt = LocalDateTime.now();
    }

    public void invalidate() {
        status = CustomScenarioStatus.INVALIDATED;
        updatedAt = LocalDateTime.now();
    }

    public void archive() {
        status = CustomScenarioStatus.ARCHIVED;
        updatedAt = LocalDateTime.now();
    }
}
