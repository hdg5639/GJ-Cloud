package gj.cloud.ops.application.preview.custom;

import gj.cloud.ops.application.preview.scenario.ScenarioModels.ScenarioPlan;
import gj.cloud.ops.domain.preview.enums.CustomScenarioVisibility;

public record CustomScenarioExport(
        String format,
        String name,
        String description,
        String naturalLanguageSource,
        CustomScenarioVisibility visibility,
        ScenarioPlan definition
) {
    public static final String FORMAT = "gamjabox.custom-scenario.v1";
}
