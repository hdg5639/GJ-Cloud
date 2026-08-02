package gj.cloud.ops.application.preview.scenario;

import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.CapabilityType;
import gj.cloud.ops.application.preview.analysis.RiskLevel;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.BindingTarget;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.CompiledScenario;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.CompiledScenarioStage;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.CompilationStatus;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.DiagnosticStatus;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.ResolutionStrategy;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.ScenarioDiagnostic;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.ScenarioPlan;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.ScenarioStagePlan;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.StageInputBinding;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.StageOutputBinding;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.StageRole;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.VerificationContract;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.VerificationType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 의미 ScenarioPlan을 현재 OpenAPI에서 실제로 발견된 Capability에 결합한다.
 * endpoint나 path를 새로 만들어내는 경로는 없다.
 */
@Component
public class ScenarioCompiler {

    private static final Pattern PATH_PARAM = Pattern.compile("\\{([^}]+)}");

    public record CompilationResult(List<CompiledScenario> scenarios, List<ScenarioDiagnostic> diagnostics) {
    }

    public CompilationResult compile(List<ScenarioPlan> plans, List<Capability> capabilities) {
        Map<String, Capability> catalog = capabilities.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Capability::id, capability -> capability, (left, right) -> left,
                        LinkedHashMap::new));
        List<CompiledScenario> scenarios = new ArrayList<>();
        List<ScenarioDiagnostic> allDiagnostics = new ArrayList<>();

        for (ScenarioPlan plan : plans) {
            List<ScenarioDiagnostic> diagnostics = new ArrayList<>();
            List<CompiledScenarioStage> stages = new ArrayList<>();
            boolean requiredMissing = false;
            boolean partial = false;

            for (ScenarioStagePlan stage : plan.stages()) {
                Capability capability = stage.capabilityRequirement() == null
                        ? null : catalog.get(stage.capabilityRequirement());
                if (stage.capabilityRequirement() != null && capability == null) {
                    ScenarioDiagnostic diagnostic = new ScenarioDiagnostic(
                            plan.id(), stage.id(),
                            stage.required() ? DiagnosticStatus.UNSUPPORTED : DiagnosticStatus.PARTIALLY_SUPPORTED,
                            "필요 capability를 현재 OpenAPI에서 찾지 못했습니다: " + stage.capabilityRequirement(),
                            stage.required() ? ResolutionStrategy.MARK_AS_UNSUPPORTED : ResolutionStrategy.REMOVE_STAGE,
                            null
                    );
                    diagnostics.add(diagnostic);
                    if (stage.required()) requiredMissing = true;
                    else partial = true;
                    continue;
                }
                stages.add(compileStage(plan, stage, capability));
            }

            CompilationStatus status = requiredMissing
                    ? CompilationStatus.UNSUPPORTED
                    : partial ? CompilationStatus.PARTIALLY_SUPPORTED : CompilationStatus.EXECUTABLE;
            String entryStageId = ScenarioValidator.resolveEntryStageId(plan.stages());
            CompiledScenario scenario = new CompiledScenario(
                    plan.id(), plan.name(), plan.actor(), plan.goal(),
                    entryStageId, stages, plan.scenarioState(), status,
                    diagnostics, plan.confidence(), ScenarioModels.SCHEMA_VERSION, ScenarioModels.RUNTIME_VERSION
            );
            List<String> validationErrors = ScenarioValidator.validateCompiled(scenario, catalog.keySet());
            if (!validationErrors.isEmpty()) {
                diagnostics = new ArrayList<>(diagnostics);
                for (String error : validationErrors) {
                    diagnostics.add(new ScenarioDiagnostic(
                            plan.id(), null, DiagnosticStatus.UNSUPPORTED, error,
                            ResolutionStrategy.MARK_AS_UNSUPPORTED, null));
                }
                scenario = new CompiledScenario(
                        scenario.id(), scenario.name(), scenario.actor(), scenario.goal(), scenario.entryStageId(),
                        scenario.stages(), scenario.scenarioState(), CompilationStatus.UNSUPPORTED, diagnostics,
                        scenario.confidence(), scenario.schemaVersion(), scenario.runtimeVersion());
            }
            scenarios.add(scenario);
            allDiagnostics.addAll(diagnostics);
        }
        return new CompilationResult(List.copyOf(scenarios), List.copyOf(allDiagnostics));
    }

    private CompiledScenarioStage compileStage(
            ScenarioPlan plan,
            ScenarioStagePlan stage,
            Capability capability
    ) {
        if (capability == null) {
            return new CompiledScenarioStage(
                    stage.id(), stage.role(), stage.intent(), null, null, !stage.required(),
                    stage.inputs(), stage.outputs(), stage.nextStageIds(), List.of(), List.of(),
                    localVerification(stage), RiskLevel.SAFE);
        }

        List<StageInputBinding> inputs = new ArrayList<>();
        Matcher matcher = PATH_PARAM.matcher(capability.path() == null ? "" : capability.path());
        while (matcher.find()) {
            String parameter = matcher.group(1);
            String stateKey = resolveStateKey(plan.scenarioState(), parameter, stage);
            inputs.add(new StageInputBinding(parameter, BindingTarget.PATH,
                    "$scenario." + stateKey, true));
        }
        if (capability.searchParam() != null && stage.role() == StageRole.DISCOVER
                && plan.scenarioState().contains("search")) {
            inputs.add(new StageInputBinding(capability.searchParam(), BindingTarget.QUERY,
                    "$scenario.search", false));
        }
        if (!"GET".equalsIgnoreCase(capability.method()) && !"DELETE".equalsIgnoreCase(capability.method())) {
            for (String field : capability.fields()) {
                inputs.add(new StageInputBinding(field, BindingTarget.BODY, "$scenario." + field, true));
            }
        }

        List<StageOutputBinding> outputs = outputBindings(stage, capability);
        return new CompiledScenarioStage(
                stage.id(), stage.role(), stage.intent(), capability.id(), capability.operationId(),
                !stage.required(), stage.inputs(), stage.outputs(), stage.nextStageIds(), inputs, outputs,
                verification(stage, capability), capability.risk());
    }

    private List<StageOutputBinding> outputBindings(ScenarioStagePlan stage, Capability capability) {
        List<StageOutputBinding> outputs = new ArrayList<>();
        if (stage.role() == StageRole.AUTHENTICATE) {
            List<String> candidates = new ArrayList<>();
            if (capability.accessTokenPath() != null) candidates.add(capability.accessTokenPath());
            candidates.addAll(List.of("data.accessToken", "data.token", "accessToken", "token"));
            outputs.add(new StageOutputBinding(candidates, "authToken", true));
        }
        if (capability.type() == CapabilityType.CREATE && stage.outputs().contains("createdId")) {
            outputs.add(new StageOutputBinding(
                    identifierCandidates(capability.resourceName()), "createdId", false));
        }
        if (stage.outputs().contains("collection") || stage.outputs().contains("authenticatedCollection")) {
            List<String> candidates = capability.collectionPath() == null
                    ? List.of("data.content", "data", "content", "items", "$")
                    : List.of(capability.collectionPath(), "data", "$");
            outputs.add(new StageOutputBinding(candidates,
                    stage.outputs().contains("collection") ? "collection" : "authenticatedCollection", false));
        }
        if (stage.outputs().contains("selectedResource") || stage.outputs().contains("verifiedResource")) {
            outputs.add(new StageOutputBinding(List.of("data", "result", "payload", "$"),
                    stage.outputs().contains("selectedResource") ? "selectedResource" : "verifiedResource", false));
        }
        if (stage.outputs().contains("trackedStatus") && capability.pollHint() != null) {
            outputs.add(new StageOutputBinding(List.of(capability.pollHint().statusPath()), "trackedStatus", false));
        }
        return outputs;
    }

    private List<String> identifierCandidates(String resourceName) {
        String resource = resourceName == null ? "resource" : resourceName.trim();
        String singular = singularize(resource);
        String idField = singular + "Id";
        return List.of(
                "data.id", "result.id", "payload.id", "id",
                "data." + idField, "result." + idField, "payload." + idField, idField,
                "data." + singular + ".id", "result." + singular + ".id", "payload." + singular + ".id",
                "data.data.id", "data.result.id", "data.payload.id"
        ).stream().distinct().toList();
    }

    private String singularize(String resource) {
        String lower = resource.toLowerCase(Locale.ROOT);
        if (lower.endsWith("ies") && resource.length() > 3) {
            return resource.substring(0, resource.length() - 3) + "y";
        }
        if (lower.endsWith("ses") && resource.length() > 3) {
            return resource.substring(0, resource.length() - 2);
        }
        if (lower.endsWith("s") && !lower.endsWith("ss") && resource.length() > 1) {
            return resource.substring(0, resource.length() - 1);
        }
        return resource;
    }

    private VerificationContract verification(ScenarioStagePlan stage, Capability capability) {
        if (stage.verificationIntent() == null) return null;
        String path = null;
        String expected = null;
        List<String> accepted = List.of();
        if (stage.verificationIntent() == VerificationType.RESOURCE_EXISTS
                || stage.verificationIntent() == VerificationType.COLLECTION_CONTAINS
                || stage.verificationIntent() == VerificationType.FIELD_EQUALS) {
            path = stage.verificationIntent() == VerificationType.FIELD_EQUALS
                    ? lastPathParameter(capability.path()) : "id";
            expected = stage.inputs().contains("createdId")
                    ? "$scenario.createdId"
                    : stage.inputs().contains("selectedId") ? "$scenario.selectedId" : null;
        }
        if (stage.verificationIntent() == VerificationType.STATE_EQUALS && capability.pollHint() != null) {
            path = capability.pollHint().statusPath();
            accepted = capability.pollHint().terminalValues();
        }
        if (stage.verificationIntent() == VerificationType.OUTPUT_EXTRACTABLE) {
            expected = stage.role() == StageRole.AUTHENTICATE ? "$scenario.authToken" : "$scenario.createdId";
        }
        return new VerificationContract(stage.verificationIntent(), capability.id(), path, expected,
                accepted, stage.required());
    }

    private VerificationContract localVerification(ScenarioStagePlan stage) {
        if (stage.verificationIntent() == null) return null;
        return new VerificationContract(stage.verificationIntent(), null, null, null, List.of(), stage.required());
    }

    private String resolveStateKey(List<String> state, String parameter, ScenarioStagePlan stage) {
        String lower = parameter.toLowerCase(Locale.ROOT);
        if (lower.endsWith("id")) {
            for (String preferred : List.of("createdId", "selectedId", "targetId")) {
                if (stage.inputs().contains(preferred)) return preferred;
            }
            if (stage.inputs().contains(parameter)) return parameter;
            if (state.contains(parameter)) return parameter;
            return state.stream().filter(value -> value.toLowerCase(Locale.ROOT).endsWith("id"))
                    .findFirst().orElse(parameter);
        }
        if (state.contains(parameter)) return parameter;
        return parameter;
    }

    private String lastPathParameter(String path) {
        Matcher matcher = PATH_PARAM.matcher(path == null ? "" : path);
        String last = "id";
        while (matcher.find()) last = matcher.group(1);
        return last;
    }
}
