package gj.cloud.ops.application.preview.scenario;

import gj.cloud.ops.application.preview.analysis.AutomationPolicy;
import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.CapabilityKind;
import gj.cloud.ops.application.preview.analysis.CapabilityType;
import gj.cloud.ops.application.preview.analysis.OpenApiEvidence;
import gj.cloud.ops.application.preview.analysis.RiskLevel;
import gj.cloud.ops.application.preview.dto.PreviewAnalyzeRequest.Purpose;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.PlanningSource;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.PreviewMode;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.ServiceUnderstanding;
import gj.cloud.ops.application.preview.scenario.ai.AiScenarioPlanner;
import gj.cloud.ops.application.preview.scenario.ai.AiScenarioPlanner.PlanningAttempt;
import gj.cloud.ops.application.preview.scenario.ai.ScenarioProposalNormalizer.NormalizedProposal;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ScenarioGenerationServiceTest {

    private final RuleBasedScenarioPlanner rulePlanner = new RuleBasedScenarioPlanner();
    private final ScenarioCompiler compiler = new ScenarioCompiler();
    private final AiScenarioPlanner aiPlanner = mock(AiScenarioPlanner.class);
    private final ScenarioGenerationService service =
            new ScenarioGenerationService(rulePlanner, compiler, aiPlanner);

    @Test
    void choosesConfidentExecutableLlmPlan() {
        List<Capability> capabilities = capabilities();
        var rule = rulePlanner.plan(evidence(), "프로젝트 서비스", Purpose.PRODUCT_LIKE, capabilities);
        ServiceUnderstanding understanding = new ServiceUnderstanding(
                "PROJECT_MANAGEMENT", "PRODUCT_SERVICE", rule.understanding().actors(),
                List.of("Project"), List.of("프로젝트를 조회하고 생성"), 0.91, List.of("Project schema"));
        when(aiPlanner.plan(anyString(), any(), any(), any(), anyList()))
                .thenReturn(new PlanningAttempt(
                        new NormalizedProposal(understanding, rule.plans(), List.of()), true, "scenario-planner-v1"));

        var result = service.generate(
                "user-1", evidence(), "프로젝트 서비스", Purpose.PRODUCT_LIKE,
                capabilities, PreviewMode.SCENARIO_PREVIEW);

        assertThat(result.planningSource()).isEqualTo(PlanningSource.LLM);
        assertThat(result.promptVersion()).isEqualTo("scenario-planner-v1");
        assertThat(result.serviceUnderstanding().domain()).isEqualTo("PROJECT_MANAGEMENT");
        assertThat(result.scenarios()).anyMatch(scenario ->
                scenario.status() == ScenarioModels.CompilationStatus.EXECUTABLE);
    }

    @Test
    void lowConfidenceAiResultFallsBackToDeterministicPlanner() {
        List<Capability> capabilities = capabilities();
        var rule = rulePlanner.plan(evidence(), null, Purpose.API_TEST, capabilities);
        ServiceUnderstanding lowConfidence = new ServiceUnderstanding(
                "UNKNOWN", "DEVELOPER_API", rule.understanding().actors(),
                List.of("Project"), List.of("알 수 없음"), 0.2, List.of());
        when(aiPlanner.plan(anyString(), any(), any(), any(), anyList()))
                .thenReturn(new PlanningAttempt(
                        new NormalizedProposal(lowConfidence, rule.plans(), List.of()), true, "scenario-planner-v1"));

        var result = service.generate(
                "user-1", evidence(), null, Purpose.API_TEST,
                capabilities, PreviewMode.SCENARIO_PREVIEW);

        assertThat(result.planningSource()).isEqualTo(PlanningSource.RULE_BASED);
        assertThat(result.serviceUnderstanding().domain()).isEqualTo(rule.understanding().domain());
        assertThat(result.diagnostics()).anyMatch(diagnostic -> diagnostic.message().contains("신뢰도가 낮아"));
    }

    @Test
    void operationModeNeverSpendsAnAiCall() {
        var result = service.generate(
                "user-1", evidence(), null, Purpose.API_TEST,
                capabilities(), PreviewMode.OPERATION_PREVIEW);

        assertThat(result.planningSource()).isEqualTo(PlanningSource.OPERATION_ONLY);
        assertThat(result.previewMode()).isEqualTo(PreviewMode.OPERATION_PREVIEW);
        verifyNoInteractions(aiPlanner);
    }

    private OpenApiEvidence evidence() {
        return new OpenApiEvidence("Project API", "1", List.of("https://api.example.com"),
                List.of(), List.of(), 0);
    }

    private List<Capability> capabilities() {
        return List.of(
                capability("projects.list", CapabilityType.LIST, "listProjects", "/projects", "GET", List.of()),
                capability("projects.detail", CapabilityType.DETAIL, "getProject", "/projects/{projectId}", "GET",
                        List.of()),
                capability("projects.create", CapabilityType.CREATE, "createProject", "/projects", "POST",
                        List.of("name"))
        );
    }

    private Capability capability(
            String id,
            CapabilityType type,
            String operationId,
            String path,
            String method,
            List<String> fields
    ) {
        return new Capability(
                id, "projects", type, operationId, path, method,
                false, false, false, "HIGH", List.of("test"), fields, null, null,
                type == CapabilityType.CREATE ? RiskLevel.STATE_CHANGING : RiskLevel.SAFE,
                type == CapabilityType.CREATE ? AutomationPolicy.USER_INITIATED : AutomationPolicy.AUTO_SAFE,
                type == CapabilityType.LIST ? "data" : null, null,
                type == CapabilityType.CREATE ? CapabilityKind.MUTATION : CapabilityKind.QUERY,
                null, List.of()
        );
    }
}
