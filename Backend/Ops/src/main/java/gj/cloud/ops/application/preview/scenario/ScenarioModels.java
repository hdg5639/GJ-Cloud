package gj.cloud.ops.application.preview.scenario;

import gj.cloud.ops.application.preview.analysis.RiskLevel;

import java.util.List;

/**
 * Scenario-first Auto Preview의 직렬화 계약.
 *
 * 의미 시나리오(ScenarioPlan)와 특정 OpenAPI 분석 결과에 결합된 실행 리비전(CompiledScenario)을
 * 의도적으로 분리한다. UI projection은 이 모델을 소비할 뿐 시나리오 의미를 변경하지 않는다.
 */
public final class ScenarioModels {

    public static final String SCHEMA_VERSION = "1.0";
    public static final String RUNTIME_VERSION = "3.0.0";

    public enum PreviewMode {
        SCENARIO_PREVIEW,
        INFERRED_SCENARIO_PREVIEW,
        OPERATION_PREVIEW
    }

    public enum PlanningSource {
        LLM,
        RULE_BASED,
        OPERATION_ONLY
    }

    public enum StageRole {
        ENTRY,
        AUTHENTICATE,
        SELECT_CONTEXT,
        DISCOVER,
        INSPECT,
        SELECT,
        COMPARE,
        ACCUMULATE,
        CONFIGURE,
        PREPARE,
        REVIEW,
        COMMIT,
        WAIT,
        VERIFY,
        TRACK,
        RECOVER,
        CONTINUE,
        COMPLETE
    }

    public enum CompilationStatus {
        EXECUTABLE,
        PARTIALLY_SUPPORTED,
        UNSUPPORTED
    }

    public enum DiagnosticStatus {
        SUPPORTED,
        PARTIALLY_SUPPORTED,
        UNSUPPORTED
    }

    public enum ResolutionStrategy {
        REMOVE_STAGE,
        MERGE_STAGE,
        REPLACE_WITH_LOCAL_STATE,
        REPLACE_VERIFICATION_METHOD,
        DOWNGRADE_TO_READ_ONLY,
        MARK_AS_UNSUPPORTED,
        REQUEST_MANUAL_BINDING
    }

    public enum VerificationType {
        HTTP_STATUS_MATCH,
        RESPONSE_SCHEMA_VALID,
        RESOURCE_EXISTS,
        RESOURCE_NOT_EXISTS,
        FIELD_EQUALS,
        STATE_EQUALS,
        COLLECTION_CONTAINS,
        COLLECTION_EXCLUDES,
        OUTPUT_EXTRACTABLE
    }

    public enum BindingTarget {
        PATH,
        QUERY,
        BODY,
        HEADER
    }

    public record ServiceActor(String id, String label) {
    }

    public record ServiceUnderstanding(
            String domain,
            String serviceType,
            List<ServiceActor> actors,
            List<String> coreEntities,
            List<String> primaryGoals,
            double confidence,
            List<String> evidence
    ) {
        public ServiceUnderstanding {
            actors = immutable(actors);
            coreEntities = immutable(coreEntities);
            primaryGoals = immutable(primaryGoals);
            evidence = immutable(evidence);
        }
    }

    /**
     * OpenAPI operation과 아직 결합되지 않은 의미 시나리오.
     * capabilityRequirement는 operationId가 아니라 재컴파일 가능한 semantic capability id다.
     */
    public record ScenarioPlan(
            String id,
            String name,
            String actor,
            String goal,
            List<String> entryConditions,
            List<ScenarioStagePlan> stages,
            List<String> scenarioState,
            double confidence,
            List<String> evidence
    ) {
        public ScenarioPlan {
            entryConditions = immutable(entryConditions);
            stages = immutable(stages);
            scenarioState = immutable(scenarioState);
            evidence = immutable(evidence);
        }
    }

    public record ScenarioStagePlan(
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
        public ScenarioStagePlan {
            inputs = immutable(inputs);
            outputs = immutable(outputs);
            nextStageIds = immutable(nextStageIds);
        }
    }

    /**
     * 한 OpenAPI fingerprint에 대해 실행 가능한 형태로 컴파일된 시나리오.
     */
    public record CompiledScenario(
            String id,
            String name,
            String actor,
            String goal,
            String entryStageId,
            List<CompiledScenarioStage> stages,
            List<String> scenarioState,
            CompilationStatus status,
            List<ScenarioDiagnostic> diagnostics,
            double confidence,
            String schemaVersion,
            String runtimeVersion
    ) {
        public CompiledScenario {
            stages = immutable(stages);
            scenarioState = immutable(scenarioState);
            diagnostics = immutable(diagnostics);
        }
    }

    public record CompiledScenarioStage(
            String id,
            StageRole role,
            String intent,
            String capabilityId,
            String operationId,
            boolean optional,
            List<String> inputs,
            List<String> outputs,
            List<String> nextStageIds,
            List<StageInputBinding> inputBindings,
            List<StageOutputBinding> outputBindings,
            VerificationContract verification,
            RiskLevel risk
    ) {
        public CompiledScenarioStage {
            inputs = immutable(inputs);
            outputs = immutable(outputs);
            nextStageIds = immutable(nextStageIds);
            inputBindings = immutable(inputBindings);
            outputBindings = immutable(outputBindings);
        }

        public boolean executableOperation() {
            return capabilityId != null && !capabilityId.isBlank();
        }
    }

    public record StageInputBinding(
            String target,
            BindingTarget targetKind,
            String source,
            boolean required
    ) {
    }

    /**
     * 후보 경로를 순서대로 검사해 최초로 발견된 값을 scenario state에 기록한다.
     */
    public record StageOutputBinding(
            List<String> fromCandidates,
            String to,
            boolean sensitive
    ) {
        public StageOutputBinding {
            fromCandidates = immutable(fromCandidates);
        }
    }

    public record VerificationContract(
            VerificationType type,
            String capabilityId,
            String responsePath,
            String expectedSource,
            List<String> acceptedValues,
            boolean required
    ) {
        public VerificationContract {
            acceptedValues = immutable(acceptedValues);
        }
    }

    public record ScenarioDiagnostic(
            String scenarioId,
            String stageId,
            DiagnosticStatus status,
            String message,
            ResolutionStrategy resolution,
            String replacementCapabilityId
    ) {
    }

    public record ScenarioGenerationResult(
            ServiceUnderstanding serviceUnderstanding,
            List<ScenarioPlan> plans,
            List<CompiledScenario> scenarios,
            List<ScenarioDiagnostic> diagnostics,
            PreviewMode previewMode,
            PlanningSource planningSource,
            String promptVersion
    ) {
        public ScenarioGenerationResult {
            plans = immutable(plans);
            scenarios = immutable(scenarios);
            diagnostics = immutable(diagnostics);
        }
    }

    private static <T> List<T> immutable(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private ScenarioModels() {
    }
}
