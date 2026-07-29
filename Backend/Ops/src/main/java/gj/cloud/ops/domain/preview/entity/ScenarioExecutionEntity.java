package gj.cloud.ops.domain.preview.entity;

import gj.cloud.ops.domain.preview.enums.ScenarioExecutionStatus;
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
@Table(name = "scenario_executions")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScenarioExecutionEntity {

    @Id
    private String id;

    @Column(name = "suite_run_id", nullable = false)
    private String suiteRunId;

    @Column(name = "scenario_id", nullable = false)
    private String scenarioId;

    @Column(name = "scenario_revision_id", nullable = false)
    private String scenarioRevisionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_status", nullable = false)
    private ScenarioExecutionStatus executionStatus;

    @Column(name = "input_snapshot_json", nullable = false, columnDefinition = "TEXT")
    private String inputSnapshotJson;

    @Column(name = "state_snapshot_json", nullable = false, columnDefinition = "TEXT")
    private String stateSnapshotJson;

    @Column(name = "result_summary_json", nullable = false, columnDefinition = "TEXT")
    private String resultSummaryJson;

    @Column(name = "failure_stage_id")
    private String failureStageId;

    @Column(name = "failure_request_json", columnDefinition = "TEXT")
    private String failureRequestJson;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at", nullable = false)
    private LocalDateTime completedAt;

    public static ScenarioExecutionEntity completed(
            String suiteRunId,
            String scenarioId,
            String scenarioRevisionId,
            ScenarioExecutionStatus status,
            String inputSnapshotJson,
            String stateSnapshotJson,
            String resultSummaryJson,
            String failureStageId,
            String failureRequestJson,
            LocalDateTime startedAt,
            LocalDateTime completedAt
    ) {
        return ScenarioExecutionEntity.builder()
                .id(UUID.randomUUID().toString())
                .suiteRunId(suiteRunId)
                .scenarioId(scenarioId)
                .scenarioRevisionId(scenarioRevisionId)
                .executionStatus(status)
                .inputSnapshotJson(inputSnapshotJson)
                .stateSnapshotJson(stateSnapshotJson)
                .resultSummaryJson(resultSummaryJson)
                .failureStageId(failureStageId)
                .failureRequestJson(failureRequestJson)
                .startedAt(startedAt)
                .completedAt(completedAt)
                .build();
    }
}
