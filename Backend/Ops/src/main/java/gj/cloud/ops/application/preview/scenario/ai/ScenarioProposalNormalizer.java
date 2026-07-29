package gj.cloud.ops.application.preview.scenario.ai;

import gj.cloud.ops.application.preview.scenario.ScenarioModels.ScenarioPlan;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.ScenarioStagePlan;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.ServiceActor;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.ServiceUnderstanding;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.StageRole;
import gj.cloud.ops.application.preview.scenario.ScenarioValidator;
import gj.cloud.ops.application.preview.scenario.ai.AiScenarioProposal.AiActor;
import gj.cloud.ops.application.preview.scenario.ai.AiScenarioProposal.AiScenario;
import gj.cloud.ops.application.preview.scenario.ai.AiScenarioProposal.AiScenarioStage;
import gj.cloud.ops.application.preview.scenario.ai.AiScenarioProposal.AiServiceUnderstanding;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * LLM 구조화 출력을 Scenario Core 계약으로 옮기는 비신뢰 경계.
 */
@Component
public class ScenarioProposalNormalizer {

    static final int MAX_SCENARIOS = 6;
    static final int MAX_STAGES = 16;
    private static final int MAX_STATE_KEYS = 40;
    private static final Pattern SAFE_ID = Pattern.compile("[a-zA-Z0-9][a-zA-Z0-9._:-]{0,99}");
    private static final Pattern SAFE_STATE = Pattern.compile("[a-zA-Z][a-zA-Z0-9_]{0,63}");

    public record NormalizedProposal(
            ServiceUnderstanding understanding,
            List<ScenarioPlan> plans,
            List<String> errors
    ) {
    }

    public NormalizedProposal normalize(AiScenarioProposal proposal, Set<String> allowedCapabilityIds) {
        if (proposal == null) {
            return new NormalizedProposal(null, List.of(), List.of("AI scenario proposal이 비어있음"));
        }
        List<String> errors = new ArrayList<>();
        ServiceUnderstanding understanding = normalizeUnderstanding(proposal.understanding(), errors);
        List<ScenarioPlan> plans = new ArrayList<>();
        Set<String> scenarioIds = new LinkedHashSet<>();
        List<AiScenario> candidates = proposal.scenarios() == null ? List.of() : proposal.scenarios();
        if (candidates.size() > MAX_SCENARIOS) {
            errors.add("AI scenario 개수가 상한(" + MAX_SCENARIOS + ")을 초과해 나머지를 제외함");
        }
        for (AiScenario candidate : candidates.stream().limit(MAX_SCENARIOS).toList()) {
            ScenarioPlan plan = normalizeScenario(candidate, understanding, allowedCapabilityIds, errors);
            if (plan == null) continue;
            if (!scenarioIds.add(plan.id())) {
                errors.add("중복 AI scenario id(" + plan.id() + ")");
                continue;
            }
            List<String> validationErrors = ScenarioValidator.validatePlan(plan);
            if (!validationErrors.isEmpty()) {
                validationErrors.forEach(error -> errors.add("AI scenario 제거: " + error));
                continue;
            }
            plans.add(plan);
        }
        return new NormalizedProposal(understanding, List.copyOf(plans), List.copyOf(errors));
    }

    private ServiceUnderstanding normalizeUnderstanding(
            AiServiceUnderstanding source,
            List<String> errors
    ) {
        if (source == null || blank(source.domain()) || blank(source.serviceType())) {
            errors.add("AI service understanding의 domain/serviceType이 비어있음");
            return null;
        }
        List<ServiceActor> actors = safe(source.actors()).stream()
                .filter(actor -> actor != null && safeId(actor.id()) && !blank(actor.label()))
                .limit(8)
                .map(actor -> new ServiceActor(actor.id(), actor.label().trim()))
                .toList();
        if (actors.isEmpty()) {
            errors.add("AI service understanding에 유효한 actor가 없음");
            return null;
        }
        return new ServiceUnderstanding(
                normalizeEnumLike(source.domain()),
                normalizeEnumLike(source.serviceType()),
                actors,
                safeStrings(source.coreEntities(), 16),
                safeStrings(source.primaryGoals(), 12),
                confidence(source.confidence()),
                safeStrings(source.evidence(), 16)
        );
    }

    private ScenarioPlan normalizeScenario(
            AiScenario source,
            ServiceUnderstanding understanding,
            Set<String> allowedCapabilityIds,
            List<String> errors
    ) {
        if (source == null || !safeId(source.id()) || blank(source.name()) || blank(source.goal())) {
            errors.add("유효하지 않은 AI scenario 기본 필드");
            return null;
        }
        List<AiScenarioStage> sourceStages = safe(source.stages());
        if (sourceStages.isEmpty() || sourceStages.size() > MAX_STAGES) {
            errors.add(source.id() + ": stage 개수가 1.." + MAX_STAGES + " 범위를 벗어남");
            return null;
        }
        List<String> scenarioState = safe(source.scenarioState()).stream()
                .filter(ScenarioProposalNormalizer::safeState)
                .distinct()
                .limit(MAX_STATE_KEYS)
                .toList();
        Set<String> actorIds = understanding == null
                ? Set.of()
                : understanding.actors().stream().map(ServiceActor::id).collect(java.util.stream.Collectors.toSet());
        String actor = actorIds.contains(source.actor())
                ? source.actor()
                : actorIds.stream().findFirst().orElse("api_developer");

        List<ScenarioStagePlan> stages = new ArrayList<>();
        Set<String> stageIds = new LinkedHashSet<>();
        for (AiScenarioStage stage : sourceStages) {
            if (stage == null || !safeId(stage.id()) || stage.role() == null || !stageIds.add(stage.id())) {
                errors.add(source.id() + ": 유효하지 않거나 중복된 stage id");
                return null;
            }
            String capability = normalizeCapabilityRequirement(stage.capabilityRequirement(), stage.role(), errors,
                    source.id(), stage.id(), allowedCapabilityIds);
            if (!localRole(stage.role()) && capability == null && stage.required()) {
                errors.add(source.id() + "/" + stage.id() + ": 실행 stage에 capability requirement가 없음");
                return null;
            }
            stages.add(new ScenarioStagePlan(
                    stage.id(), stage.role(), blank(stage.intent()) ? stage.role().name() : stage.intent().trim(),
                    capability, stage.required(),
                    safeStateKeys(stage.inputs()), safeStateKeys(stage.outputs()),
                    safe(stage.nextStageIds()).stream().filter(ScenarioProposalNormalizer::safeId).distinct().toList(),
                    stage.verificationIntent()
            ));
        }
        return new ScenarioPlan(
                source.id(), source.name().trim(), actor, source.goal().trim(),
                safeStrings(source.entryConditions(), 12), stages, scenarioState,
                confidence(source.confidence()), safeStrings(source.evidence(), 16)
        );
    }

    private String normalizeCapabilityRequirement(
            String value,
            StageRole role,
            List<String> errors,
            String scenarioId,
            String stageId,
            Set<String> allowedCapabilityIds
    ) {
        if (blank(value)) return null;
        String trimmed = value.trim();
        if (!safeId(trimmed) || trimmed.contains("/") || trimmed.contains("{") || trimmed.contains("}")) {
            errors.add(scenarioId + "/" + stageId + ": 허용되지 않는 capability requirement(" + trimmed + ")");
            return null;
        }
        if (allowedCapabilityIds == null || !allowedCapabilityIds.contains(trimmed)) {
            errors.add(scenarioId + "/" + stageId + ": catalog에 없는 capability requirement(" + trimmed + ")");
            return null;
        }
        if (localRole(role)) {
            return null;
        }
        return trimmed;
    }

    private static boolean localRole(StageRole role) {
        return switch (role) {
            case ENTRY, SELECT_CONTEXT, SELECT, COMPARE, ACCUMULATE, CONFIGURE, PREPARE, REVIEW,
                    WAIT, RECOVER, CONTINUE, COMPLETE -> true;
            case AUTHENTICATE, DISCOVER, INSPECT, COMMIT, VERIFY, TRACK -> false;
        };
    }

    private List<String> safeStateKeys(List<String> values) {
        return safe(values).stream().filter(ScenarioProposalNormalizer::safeState).distinct().limit(20).toList();
    }

    private List<String> safeStrings(List<String> values, int limit) {
        return safe(values).stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .filter(value -> value.length() <= 300)
                .distinct()
                .limit(limit)
                .toList();
    }

    private static boolean safeId(String value) {
        return value != null && SAFE_ID.matcher(value.trim()).matches();
    }

    private static boolean safeState(String value) {
        return value != null && SAFE_STATE.matcher(value.trim()).matches();
    }

    private static double confidence(double value) {
        if (!Double.isFinite(value)) return 0;
        return Math.max(0, Math.min(1, value));
    }

    private static String normalizeEnumLike(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return normalized.isBlank() ? "GENERAL_API" : normalized;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
