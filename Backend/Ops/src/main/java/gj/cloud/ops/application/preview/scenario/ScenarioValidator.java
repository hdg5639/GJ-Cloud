package gj.cloud.ops.application.preview.scenario;

import gj.cloud.ops.application.preview.scenario.ScenarioModels.CompiledScenario;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.CompiledScenarioStage;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.ScenarioPlan;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.ScenarioStagePlan;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.StageRole;
import gj.cloud.ops.application.preview.analysis.RiskLevel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * LLM이나 UI와 무관한 Scenario graph hard gate.
 */
public final class ScenarioValidator {

    private static final int MAX_STAGES = 24;

    public static List<String> validatePlan(ScenarioPlan plan) {
        List<String> errors = new ArrayList<>();
        if (plan == null) {
            return List.of("scenario plan이 null임");
        }
        if (blank(plan.id())) errors.add("scenario id가 비어있음");
        if (plan.stages().isEmpty()) errors.add(plan.id() + ": stage가 없음");
        if (plan.stages().size() > MAX_STAGES) errors.add(plan.id() + ": stage 상한(" + MAX_STAGES + ") 초과");

        Map<String, ScenarioStagePlan> stages = new HashMap<>();
        for (ScenarioStagePlan stage : plan.stages()) {
            if (stage == null || blank(stage.id())) {
                errors.add(plan.id() + ": null 또는 빈 stage id");
                continue;
            }
            if (stages.putIfAbsent(stage.id(), stage) != null) {
                errors.add(plan.id() + ": 중복 stage id(" + stage.id() + ")");
            }
        }
        errors.addAll(validateGraph(plan.id(), plan.stages().isEmpty() ? null : plan.stages().get(0).id(),
                stages, stage -> stage.nextStageIds()));
        errors.addAll(validateState(plan));
        if (plan.stages().stream().noneMatch(stage -> stage.role() == StageRole.COMPLETE)) {
            errors.add(plan.id() + ": COMPLETE stage가 없음");
        }
        return errors;
    }

    public static List<String> validateCompiled(CompiledScenario scenario, Set<String> capabilityIds) {
        List<String> errors = new ArrayList<>();
        if (scenario == null) return List.of("compiled scenario가 null임");
        Map<String, CompiledScenarioStage> stages = new HashMap<>();
        for (CompiledScenarioStage stage : scenario.stages()) {
            if (stage == null || blank(stage.id())) {
                errors.add(scenario.id() + ": null 또는 빈 compiled stage id");
                continue;
            }
            if (stages.putIfAbsent(stage.id(), stage) != null) {
                errors.add(scenario.id() + ": 중복 compiled stage id(" + stage.id() + ")");
            }
            if (stage.executableOperation() && !capabilityIds.contains(stage.capabilityId())) {
                errors.add(scenario.id() + "/" + stage.id() + ": 존재하지 않는 capability(" + stage.capabilityId() + ")");
            }
            for (var binding : stage.inputBindings()) {
                if (blank(binding.target()) || binding.targetKind() == null || blank(binding.source())) {
                    errors.add(scenario.id() + "/" + stage.id() + ": 유효하지 않은 input binding");
                }
                if (!binding.source().startsWith("$scenario.") && !binding.source().startsWith("$input.")
                        && !binding.source().startsWith("$auth.")) {
                    errors.add(scenario.id() + "/" + stage.id() + ": 허용되지 않는 binding source("
                            + binding.source() + ")");
                }
                if (binding.source().startsWith("$scenario.")) {
                    String stateKey = binding.source().substring("$scenario.".length()).split("\\.")[0];
                    if (!scenario.scenarioState().contains(stateKey)) {
                        errors.add(scenario.id() + "/" + stage.id() + ": 선언되지 않은 scenario state binding("
                                + stateKey + ")");
                    }
                }
            }
            for (var binding : stage.outputBindings()) {
                if (!scenario.scenarioState().contains(binding.to())) {
                    errors.add(scenario.id() + "/" + stage.id() + ": 선언되지 않은 scenario state output("
                            + binding.to() + ")");
                }
            }
            if (stage.executableOperation()) {
                Set<String> boundOutputs = stage.outputBindings().stream()
                        .map(binding -> binding.to())
                        .collect(java.util.stream.Collectors.toSet());
                for (String output : stage.outputs()) {
                    if (!boundOutputs.contains(output)) {
                        errors.add(scenario.id() + "/" + stage.id()
                                + ": API 응답에서 추출할 수 없는 stage output(" + output + ")");
                    }
                }
            }
        }
        errors.addAll(validateGraph(scenario.id(), scenario.entryStageId(), stages,
                stage -> stage.nextStageIds()));
        errors.addAll(validateSafety(scenario, stages));
        return errors;
    }

    private static List<String> validateSafety(
            CompiledScenario scenario,
            Map<String, CompiledScenarioStage> stages
    ) {
        List<String> errors = new ArrayList<>();
        if (blank(scenario.entryStageId()) || !stages.containsKey(scenario.entryStageId())) return errors;

        record Traversal(String stageId, boolean reviewed) {
        }
        ArrayDeque<Traversal> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.add(new Traversal(scenario.entryStageId(), false));
        while (!queue.isEmpty()) {
            Traversal current = queue.removeFirst();
            String visitKey = current.stageId() + ":" + current.reviewed();
            if (!visited.add(visitKey)) continue;
            CompiledScenarioStage stage = stages.get(current.stageId());
            if (stage == null) continue;
            if (stage.role() == StageRole.COMMIT && stage.risk() != null && stage.risk() != RiskLevel.SAFE) {
                if (!current.reviewed()) {
                    errors.add(scenario.id() + "/" + stage.id()
                            + ": 상태 변경 COMMIT 이전에 REVIEW가 보장되지 않음");
                }
                if (!canReachVerification(stage.id(), stages, new HashSet<>())) {
                    errors.add(scenario.id() + "/" + stage.id()
                            + ": 상태 변경 COMMIT 이후 VERIFY/TRACK stage가 없음");
                }
            }
            boolean reviewed = current.reviewed() || stage.role() == StageRole.REVIEW;
            for (String next : stage.nextStageIds()) queue.addLast(new Traversal(next, reviewed));
        }
        return errors.stream().distinct().toList();
    }

    private static boolean canReachVerification(
            String stageId,
            Map<String, CompiledScenarioStage> stages,
            Set<String> visited
    ) {
        if (!visited.add(stageId)) return false;
        CompiledScenarioStage stage = stages.get(stageId);
        if (stage == null) return false;
        for (String nextId : stage.nextStageIds()) {
            CompiledScenarioStage next = stages.get(nextId);
            if (next == null) continue;
            if (next.role() == StageRole.VERIFY || next.role() == StageRole.TRACK) return true;
            if (canReachVerification(nextId, stages, visited)) return true;
        }
        return false;
    }

    private static List<String> validateState(ScenarioPlan plan) {
        List<String> errors = new ArrayList<>();
        Set<String> available = new HashSet<>(plan.scenarioState());
        // scenarioState는 전체 계약이고, 실제 producer 검사는 순서대로 별도 집합에서 수행한다.
        Set<String> produced = new HashSet<>();
        for (ScenarioStagePlan stage : plan.stages()) {
            for (String input : stage.inputs()) {
                if (!available.contains(input)) {
                    errors.add(plan.id() + "/" + stage.id() + ": 선언되지 않은 state input(" + input + ")");
                }
                // PREPARE/SELECT/AUTHENTICATE는 사용자가 값을 생산하는 stage이므로 선행 producer 불필요.
                if (!produced.contains(input)
                        && stage.role() != StageRole.PREPARE
                        && stage.role() != StageRole.SELECT
                        && stage.role() != StageRole.SELECT_CONTEXT
                        && stage.role() != StageRole.AUTHENTICATE) {
                    errors.add(plan.id() + "/" + stage.id() + ": 선행 producer가 없는 state input(" + input + ")");
                }
            }
            for (String output : stage.outputs()) {
                if (!available.contains(output)) {
                    errors.add(plan.id() + "/" + stage.id() + ": 선언되지 않은 state output(" + output + ")");
                }
                produced.add(output);
            }
        }
        return errors;
    }

    private interface NextIds<T> {
        List<String> get(T stage);
    }

    private static <T> List<String> validateGraph(
            String scenarioId,
            String entryId,
            Map<String, T> stages,
            NextIds<T> nextIds
    ) {
        List<String> errors = new ArrayList<>();
        if (blank(entryId) || !stages.containsKey(entryId)) {
            errors.add(scenarioId + ": entry stage를 찾을 수 없음(" + entryId + ")");
            return errors;
        }
        for (Map.Entry<String, T> entry : stages.entrySet()) {
            for (String next : nextIds.get(entry.getValue())) {
                if (!stages.containsKey(next)) {
                    errors.add(scenarioId + ": 존재하지 않는 stage 연결(" + entry.getKey() + " -> " + next + ")");
                }
            }
        }

        Set<String> visited = new HashSet<>();
        Set<String> onStack = new HashSet<>();
        ArrayDeque<String> stack = new ArrayDeque<>();
        if (cycle(entryId, stages, nextIds, visited, onStack, stack)) {
            errors.add(scenarioId + ": illegal cycle 감지(" + String.join(" -> ", stack) + ")");
        }
        for (String stageId : stages.keySet()) {
            if (!visited.contains(stageId)) errors.add(scenarioId + ": 도달 불가능 stage(" + stageId + ")");
        }
        return errors;
    }

    private static <T> boolean cycle(
            String current,
            Map<String, T> stages,
            NextIds<T> nextIds,
            Set<String> visited,
            Set<String> onStack,
            ArrayDeque<String> stack
    ) {
        visited.add(current);
        onStack.add(current);
        stack.addLast(current);
        for (String next : nextIds.get(stages.get(current))) {
            if (!stages.containsKey(next)) continue;
            if (onStack.contains(next)) {
                stack.addLast(next);
                return true;
            }
            if (!visited.contains(next) && cycle(next, stages, nextIds, visited, onStack, stack)) return true;
        }
        onStack.remove(current);
        stack.removeLast();
        return false;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private ScenarioValidator() {
    }
}
