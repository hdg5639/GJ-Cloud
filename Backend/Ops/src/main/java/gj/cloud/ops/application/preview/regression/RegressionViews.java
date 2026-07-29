package gj.cloud.ops.application.preview.regression;

import gj.cloud.ops.domain.preview.enums.RegressionRunStatus;
import gj.cloud.ops.domain.preview.enums.RegressionTriggerType;
import gj.cloud.ops.domain.preview.enums.ScenarioExecutionStatus;

import java.time.LocalDateTime;
import java.util.List;

public final class RegressionViews {

    public record SuiteView(
            String id,
            String serviceId,
            String name,
            String description,
            String apiDocsUrl,
            String apiBaseUrl,
            List<String> scenarioIds,
            String deploymentTargetId,
            boolean runOnDeployment,
            boolean allowStateChangingOnDeployment,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record RunView(
            String id,
            String suiteId,
            RegressionRunStatus status,
            RegressionTriggerType triggerType,
            String triggerReference,
            int totalCount,
            int passedCount,
            int failedCount,
            Object summary,
            List<ScenarioExecutionView> executions,
            LocalDateTime startedAt,
            LocalDateTime completedAt,
            LocalDateTime createdAt
    ) {
    }

    public record ScenarioExecutionView(
            String id,
            String scenarioId,
            String scenarioRevisionId,
            ScenarioExecutionStatus status,
            Object inputSnapshot,
            Object stateSnapshot,
            Object result,
            String failureStageId,
            Object failureRequest,
            LocalDateTime startedAt,
            LocalDateTime completedAt
    ) {
    }

    private RegressionViews() {
    }
}
