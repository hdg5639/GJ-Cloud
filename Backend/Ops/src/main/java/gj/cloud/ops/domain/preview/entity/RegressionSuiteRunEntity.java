package gj.cloud.ops.domain.preview.entity;

import gj.cloud.ops.domain.preview.enums.RegressionRunStatus;
import gj.cloud.ops.domain.preview.enums.RegressionTriggerType;
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
@Table(name = "regression_suite_runs")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegressionSuiteRunEntity {

    @Id
    private String id;

    @Column(name = "suite_id", nullable = false)
    private String suiteId;

    @Column(name = "owner_id", nullable = false)
    private String ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RegressionRunStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false)
    private RegressionTriggerType triggerType;

    @Column(name = "trigger_reference", length = 100)
    private String triggerReference;

    @Column(name = "input_ciphertext", nullable = false, columnDefinition = "TEXT")
    private String inputCiphertext;

    @Column(name = "summary_json", columnDefinition = "TEXT")
    private String summaryJson;

    @Column(name = "total_count", nullable = false)
    private int totalCount;

    @Column(name = "passed_count", nullable = false)
    private int passedCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static RegressionSuiteRunEntity queued(
            String suiteId,
            String ownerId,
            RegressionTriggerType triggerType,
            String triggerReference,
            String inputCiphertext
    ) {
        return RegressionSuiteRunEntity.builder()
                .id(UUID.randomUUID().toString())
                .suiteId(suiteId)
                .ownerId(ownerId)
                .status(RegressionRunStatus.QUEUED)
                .triggerType(triggerType)
                .triggerReference(triggerReference)
                .inputCiphertext(inputCiphertext)
                .createdAt(LocalDateTime.now())
                .build();
    }

    public void start(int totalCount) {
        status = RegressionRunStatus.RUNNING;
        this.totalCount = totalCount;
        startedAt = LocalDateTime.now();
    }

    public void complete(int passedCount, int failedCount, String summaryJson) {
        this.passedCount = passedCount;
        this.failedCount = failedCount;
        this.summaryJson = summaryJson;
        status = failedCount == 0 ? RegressionRunStatus.PASSED : RegressionRunStatus.FAILED;
        completedAt = LocalDateTime.now();
    }

    public void fail(String summaryJson) {
        this.summaryJson = summaryJson;
        failedCount = Math.max(1, failedCount);
        status = RegressionRunStatus.FAILED;
        if (startedAt == null) startedAt = LocalDateTime.now();
        completedAt = LocalDateTime.now();
    }

    public void replaceSensitiveInput(String safeCiphertext) {
        inputCiphertext = safeCiphertext;
    }
}
