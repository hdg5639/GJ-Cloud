package gj.cloud.ops.application.preview.regression;

import gj.cloud.ops.application.preview.analysis.AutomationPolicy;
import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.CapabilityKind;
import gj.cloud.ops.application.preview.analysis.CapabilityType;
import gj.cloud.ops.application.preview.analysis.RiskLevel;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.BindingTarget;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.CompilationStatus;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.CompiledScenario;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.CompiledScenarioStage;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.StageInputBinding;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.StageOutputBinding;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.StageRole;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.VerificationContract;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.VerificationType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ScenarioRegressionEngineTest {

    @Test
    void propagatesAnOutputIntoTheNextRequestAndReportsTheExactFailedStage() {
        RecordingTransport transport = new RecordingTransport(List.of(
                new RegressionHttpTransport.Response(
                        201, Map.of(), Map.of("data", Map.of("id", "widget-42"))),
                new RegressionHttpTransport.Response(
                        200, Map.of(), Map.of("data", Map.of("id", "different-widget")))
        ));
        ScenarioRegressionEngine engine = new ScenarioRegressionEngine(transport);
        CompiledScenario scenario = new CompiledScenario(
                "create-and-verify", "Create and verify", "developer", "verify propagation", "create",
                List.of(
                        stage(
                                "create", "widgets.create", "createWidget", "verify",
                                List.of(), List.of(new StageOutputBinding(
                                        List.of("data.id"), "widgetId", false)),
                                new VerificationContract(
                                        VerificationType.HTTP_STATUS_MATCH, null, null, null, List.of(), true),
                                RiskLevel.STATE_CHANGING),
                        stage(
                                "verify", "widgets.detail", "getWidget", null,
                                List.of(new StageInputBinding(
                                        "id", BindingTarget.PATH, "$scenario.widgetId", true)),
                                List.of(),
                                new VerificationContract(
                                        VerificationType.FIELD_EQUALS, null, "data.id",
                                        "$scenario.widgetId", List.of(), true),
                                RiskLevel.SAFE)
                ),
                List.of("widgetId"), CompilationStatus.EXECUTABLE, List.of(), 1.0, "1.0", "3.0.0"
        );

        ScenarioRegressionEngine.ScenarioResult result = engine.execute(
                scenario,
                Map.of(
                        "widgets.create", capability(
                                "widgets.create", CapabilityType.CREATE, "/widgets", "POST",
                                RiskLevel.STATE_CHANGING),
                        "widgets.detail", capability(
                                "widgets.detail", CapabilityType.DETAIL, "/widgets/{id}", "GET",
                                RiskLevel.SAFE)
                ),
                "https://api.example.com",
                Map.of(),
                Map.of("X-Test-Run", "regression"),
                true
        );

        assertThat(result.passed()).isFalse();
        assertThat(result.failureStageId()).isEqualTo("verify");
        assertThat(result.finalState()).containsEntry("widgetId", "widget-42");
        assertThat(transport.requests()).hasSize(2);
        assertThat(transport.requests().get(1).url())
                .isEqualTo("https://api.example.com/widgets/widget-42");
        assertThat(result.failureRequest()).isEqualTo(Map.of(
                "path", Map.of("id", "widget-42"),
                "query", Map.of(),
                "headers", Map.of("X-Test-Run", "regression"),
                "body", Map.of()
        ));
    }

    @Test
    void blocksDestructiveStagesBeforeAnyNetworkRequest() {
        RecordingTransport transport = new RecordingTransport(List.of());
        ScenarioRegressionEngine engine = new ScenarioRegressionEngine(transport);
        CompiledScenario scenario = new CompiledScenario(
                "delete", "Delete", "developer", "delete", "delete",
                List.of(stage(
                        "delete", "widgets.delete", "deleteWidget", null,
                        List.of(), List.of(), null, RiskLevel.DESTRUCTIVE)),
                List.of(), CompilationStatus.EXECUTABLE, List.of(), 1.0, "1.0", "3.0.0"
        );

        ScenarioRegressionEngine.ScenarioResult result = engine.execute(
                scenario,
                Map.of("widgets.delete", capability(
                        "widgets.delete", CapabilityType.DELETE, "/widgets/{id}", "DELETE",
                        RiskLevel.DESTRUCTIVE)),
                "https://api.example.com",
                Map.of(),
                Map.of(),
                true
        );

        assertThat(result.passed()).isFalse();
        assertThat(result.failureStageId()).isEqualTo("delete");
        assertThat(result.stages().get(0).error()).contains("DESTRUCTIVE");
        assertThat(transport.requests()).isEmpty();
    }

    private CompiledScenarioStage stage(
            String id,
            String capabilityId,
            String operationId,
            String nextStageId,
            List<StageInputBinding> inputs,
            List<StageOutputBinding> outputs,
            VerificationContract verification,
            RiskLevel risk
    ) {
        return new CompiledScenarioStage(
                id, StageRole.COMMIT, id, capabilityId, operationId, false,
                List.of(), List.of(), nextStageId == null ? List.of() : List.of(nextStageId),
                inputs, outputs, verification, risk);
    }

    private Capability capability(
            String id,
            CapabilityType type,
            String path,
            String method,
            RiskLevel risk
    ) {
        return new Capability(
                id, "widgets", type, id, path, method,
                false, false, false, "HIGH", List.of(), List.of(),
                null, null, risk,
                risk == RiskLevel.SAFE ? AutomationPolicy.AUTO_SAFE : AutomationPolicy.USER_INITIATED,
                null, null,
                type == CapabilityType.DETAIL ? CapabilityKind.QUERY : CapabilityKind.MUTATION,
                null, List.of()
        );
    }

    private static final class RecordingTransport implements RegressionHttpTransport {
        private final List<Response> responses;
        private final List<Request> requests = new ArrayList<>();

        private RecordingTransport(List<Response> responses) {
            this.responses = new ArrayList<>(responses);
        }

        @Override
        public Response execute(Request request) {
            requests.add(request);
            return responses.remove(0);
        }

        private List<Request> requests() {
            return requests;
        }
    }
}
