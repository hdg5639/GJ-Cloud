package gj.cloud.ops.application.preview.scenario.ai;

import gj.cloud.ops.application.preview.scenario.ScenarioModels.StageRole;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.VerificationType;
import gj.cloud.ops.application.preview.scenario.ai.AiScenarioProposal.AiActor;
import gj.cloud.ops.application.preview.scenario.ai.AiScenarioProposal.AiScenario;
import gj.cloud.ops.application.preview.scenario.ai.AiScenarioProposal.AiScenarioStage;
import gj.cloud.ops.application.preview.scenario.ai.AiScenarioProposal.AiServiceUnderstanding;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ScenarioProposalNormalizerTest {

    private final ScenarioProposalNormalizer normalizer = new ScenarioProposalNormalizer();

    @Test
    void convertsValidSemanticProposalWithoutGivingItRuntimeBindingAuthority() {
        AiScenarioProposal proposal = new AiScenarioProposal(
                understanding(0.91),
                List.of(new AiScenario(
                        "project_query_and_inspect", "프로젝트 탐색", "project_admin",
                        "프로젝트 목록에서 하나를 선택해 상세 상태를 확인", List.of("authenticated"),
                        List.of(
                                stage("discover", StageRole.DISCOVER, "projects.list", List.of(),
                                        List.of("collection"), List.of("select"), VerificationType.RESPONSE_SCHEMA_VALID),
                                stage("select", StageRole.SELECT, null, List.of(),
                                        List.of("selectedId"), List.of("inspect"), null),
                                stage("inspect", StageRole.INSPECT, "projects.detail", List.of("selectedId"),
                                        List.of("selectedResource"), List.of("complete"), VerificationType.FIELD_EQUALS),
                                stage("complete", StageRole.COMPLETE, null, List.of(), List.of(), List.of(), null)
                        ),
                        List.of("collection", "selectedId", "selectedResource"), 0.9,
                        List.of("projects.list", "projects.detail")
                ))
        );

        var result = normalizer.normalize(proposal, Set.of("projects.list", "projects.detail"));

        assertThat(result.errors()).isEmpty();
        assertThat(result.understanding().domain()).isEqualTo("PROJECT_MANAGEMENT");
        assertThat(result.plans()).hasSize(1);
        assertThat(result.plans().get(0).stages())
                .extracting(stage -> stage.capabilityRequirement())
                .containsExactly("projects.list", null, "projects.detail", null);
    }

    @Test
    void dropsCyclesAndRejectsPathOrUiLikeCapabilityValues() {
        AiScenario cycle = new AiScenario(
                "cycle", "잘못된 흐름", "project_admin", "순환", List.of(),
                List.of(
                        stage("one", StageRole.DISCOVER, "projects.list", List.of(), List.of(), List.of("two"), null),
                        stage("two", StageRole.VERIFY, "projects.detail", List.of(), List.of(), List.of("one"), null),
                        stage("complete", StageRole.COMPLETE, null, List.of(), List.of(), List.of(), null)
                ),
                List.of(), 0.8, List.of()
        );
        AiScenario unsafe = new AiScenario(
                "unsafe", "권한 침범", "project_admin", "UI 또는 path 주입 차단", List.of(),
                List.of(
                        stage("commit", StageRole.COMMIT, "/projects/{id}", List.of(), List.of(),
                                List.of("complete"), VerificationType.HTTP_STATUS_MATCH),
                        stage("complete", StageRole.COMPLETE, null, List.of(), List.of(), List.of(), null)
                ),
                List.of(), 0.8, List.of()
        );

        var result = normalizer.normalize(
                new AiScenarioProposal(understanding(0.9), List.of(cycle, unsafe)),
                Set.of("projects.list", "projects.detail"));

        assertThat(result.plans()).isEmpty();
        assertThat(result.errors())
                .anyMatch(error -> error.contains("illegal cycle"))
                .anyMatch(error -> error.contains("허용되지 않는 capability requirement"));
    }

    private AiServiceUnderstanding understanding(double confidence) {
        return new AiServiceUnderstanding(
                "project management", "product service",
                List.of(new AiActor("project_admin", "프로젝트 관리자")),
                List.of("Project"), List.of("프로젝트 조회"), confidence, List.of("Project schema")
        );
    }

    private AiScenarioStage stage(
            String id,
            StageRole role,
            String capability,
            List<String> inputs,
            List<String> outputs,
            List<String> next,
            VerificationType verification
    ) {
        return new AiScenarioStage(id, role, id, capability, true, inputs, outputs, next, verification);
    }
}
