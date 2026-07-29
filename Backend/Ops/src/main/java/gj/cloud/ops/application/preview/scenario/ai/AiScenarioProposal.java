package gj.cloud.ops.application.preview.scenario.ai;

import gj.cloud.ops.application.preview.scenario.ScenarioModels.StageRole;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.VerificationType;

import java.util.List;

/**
 * OpenAI Structured Output 전용 DTO.
 * Runtime binding/path/query/body나 UI component id는 의도적으로 표현할 수 없다.
 */
public record AiScenarioProposal(
        AiServiceUnderstanding understanding,
        List<AiScenario> scenarios
) {
    public record AiServiceUnderstanding(
            String domain,
            String serviceType,
            List<AiActor> actors,
            List<String> coreEntities,
            List<String> primaryGoals,
            double confidence,
            List<String> evidence
    ) {
    }

    public record AiActor(String id, String label) {
    }

    public record AiScenario(
            String id,
            String name,
            String actor,
            String goal,
            List<String> entryConditions,
            List<AiScenarioStage> stages,
            List<String> scenarioState,
            double confidence,
            List<String> evidence
    ) {
    }

    public record AiScenarioStage(
            String id,
            StageRole role,
            String intent,
            String capabilityRequirement,
            boolean required,
            List<String> inputs,
            List<String> outputs,
            List<String> nextStageIds,
            VerificationType verificationIntent
    ) {
    }
}
