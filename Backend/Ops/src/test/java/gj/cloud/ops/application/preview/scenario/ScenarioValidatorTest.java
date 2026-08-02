package gj.cloud.ops.application.preview.scenario;

import gj.cloud.ops.application.preview.scenario.ScenarioModels.ScenarioPlan;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.ScenarioStagePlan;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.StageRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScenarioValidatorTest {

    @Test
    void acceptsReachableAcyclicScenarioWithExplicitStateProducer() {
        ScenarioPlan plan = new ScenarioPlan(
                "create-resource", "Create resource", "developer", "Create and verify",
                List.of("server_available"),
                List.of(
                        stage("prepare", StageRole.PREPARE, List.of(), List.of("name"), "commit"),
                        stage("commit", StageRole.COMMIT, List.of("name"), List.of("createdId"), "verify"),
                        stage("verify", StageRole.VERIFY, List.of("createdId"), List.of(), "complete"),
                        stage("complete", StageRole.COMPLETE, List.of(), List.of(), null)
                ),
                List.of("name", "createdId"), 0.9, List.of("test")
        );

        assertThat(ScenarioValidator.validatePlan(plan)).isEmpty();
    }

    @Test
    void rejectsCycleAndInputWithoutProducer() {
        ScenarioPlan plan = new ScenarioPlan(
                "broken", "Broken", "developer", "Broken graph", List.of(),
                List.of(
                        stage("discover", StageRole.DISCOVER, List.of("selectedId"), List.of(), "verify"),
                        stage("verify", StageRole.VERIFY, List.of(), List.of(), "discover"),
                        stage("complete", StageRole.COMPLETE, List.of(), List.of(), null)
                ),
                List.of("selectedId"), 0.4, List.of()
        );

        assertThat(ScenarioValidator.validatePlan(plan))
                .anyMatch(error -> error.contains("illegal cycle"))
                .anyMatch(error -> error.contains("선행 producer가 없는 state input"))
                .anyMatch(error -> error.contains("도달 불가능 stage(complete)"));
    }

    @Test
    void rejectsBranchWithoutAConditionContract() {
        ScenarioPlan plan = new ScenarioPlan(
                "branch", "Branch", "developer", "Do not guess a branch", List.of(),
                List.of(
                        new ScenarioStagePlan("prepare", StageRole.PREPARE, "prepare", null, true,
                                List.of(), List.of("selectedId"), List.of("left", "right"), null),
                        stage("left", StageRole.INSPECT, List.of("selectedId"), List.of(), "complete"),
                        stage("right", StageRole.INSPECT, List.of("selectedId"), List.of(), "complete"),
                        stage("complete", StageRole.COMPLETE, List.of(), List.of(), null)
                ),
                List.of("selectedId"), 0.7, List.of()
        );

        assertThat(ScenarioValidator.validatePlan(plan))
                .anyMatch(error -> error.contains("조건 계약 없는 다중 분기"));
    }

    private ScenarioStagePlan stage(
            String id,
            StageRole role,
            List<String> inputs,
            List<String> outputs,
            String next
    ) {
        return new ScenarioStagePlan(
                id, role, id, null, true, inputs, outputs,
                next == null ? List.of() : List.of(next), null
        );
    }
}
