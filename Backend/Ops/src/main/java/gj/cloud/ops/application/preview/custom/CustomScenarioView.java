package gj.cloud.ops.application.preview.custom;

import gj.cloud.ops.application.preview.scenario.ScenarioModels.CompiledScenario;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.ScenarioPlan;
import gj.cloud.ops.domain.preview.enums.CustomScenarioStatus;
import gj.cloud.ops.domain.preview.enums.CustomScenarioVisibility;

import java.time.LocalDateTime;
import java.util.List;

public record CustomScenarioView(
        String id,
        String serviceId,
        String name,
        String description,
        String naturalLanguageSource,
        CustomScenarioStatus status,
        CustomScenarioVisibility visibility,
        ScenarioPlan definition,
        int revision,
        String openapiFingerprint,
        CompiledScenario compiledScenario,
        boolean valid,
        List<String> validationErrors,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public CustomScenarioView {
        validationErrors = validationErrors == null ? List.of() : List.copyOf(validationErrors);
    }
}
