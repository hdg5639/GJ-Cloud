package gj.cloud.ops.application.preview.scenario;

import gj.cloud.ops.application.preview.analysis.AutomationPolicy;
import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.CapabilityKind;
import gj.cloud.ops.application.preview.analysis.CapabilityType;
import gj.cloud.ops.application.preview.analysis.OpenApiEvidence;
import gj.cloud.ops.application.preview.analysis.RiskLevel;
import gj.cloud.ops.application.preview.dto.PreviewAnalyzeRequest.Purpose;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.CompiledScenario;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.CompilationStatus;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.ScenarioPlan;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.ScenarioStagePlan;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.StageRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScenarioCompilerTest {

    private final RuleBasedScenarioPlanner planner = new RuleBasedScenarioPlanner();
    private final ScenarioCompiler compiler = new ScenarioCompiler();

    @Test
    void compilesCreateOutputIntoFollowUpPathBinding() {
        List<Capability> capabilities = sampleCapabilities();
        RuleBasedScenarioPlanner.PlanningResult planned = planner.plan(
                evidence(), "사용자가 프로젝트를 생성하고 조회하는 서비스", Purpose.PRODUCT_LIKE, capabilities);

        ScenarioCompiler.CompilationResult result = compiler.compile(planned.plans(), capabilities);
        CompiledScenario scenario = result.scenarios().stream()
                .filter(candidate -> candidate.id().equals("projects-create-and-verify"))
                .findFirst().orElseThrow();

        assertThat(scenario.status()).isEqualTo(CompilationStatus.EXECUTABLE);
        assertThat(scenario.stages()).filteredOn(stage -> stage.id().equals("commit"))
                .flatExtracting(stage -> stage.outputBindings())
                .anyMatch(binding -> binding.to().equals("createdId"));
        assertThat(scenario.stages()).filteredOn(stage -> stage.id().equals("commit"))
                .flatExtracting(stage -> stage.outputBindings())
                .flatExtracting(binding -> binding.fromCandidates())
                .contains("data.projectId", "data.project.id");
        assertThat(scenario.stages()).filteredOn(stage -> stage.id().equals("verify"))
                .flatExtracting(stage -> stage.inputBindings())
                .anyMatch(binding -> binding.target().equals("projectId")
                        && binding.source().equals("$scenario.createdId"));
    }

    @Test
    void followsGraphRootEvenWhenAiReturnsStagesOutOfArrayOrder() {
        List<Capability> capabilities = sampleCapabilities();
        ScenarioPlan shuffled = new ScenarioPlan(
                "shuffled", "Shuffled", "developer", "Use graph order", List.of(),
                List.of(
                        new ScenarioStagePlan("complete", StageRole.COMPLETE, "done", null,
                                true, List.of(), List.of(), List.of(), null),
                        new ScenarioStagePlan("verify", StageRole.VERIFY, "verify", "projects.detail",
                                true, List.of("createdId"), List.of("verifiedResource"), List.of("complete"),
                                ScenarioModels.VerificationType.RESOURCE_EXISTS),
                        new ScenarioStagePlan("prepare", StageRole.PREPARE, "prepare", null,
                                true, List.of(), List.of("name"), List.of("review"), null),
                        new ScenarioStagePlan("review", StageRole.REVIEW, "review", null,
                                true, List.of("name"), List.of(), List.of("commit"), null),
                        new ScenarioStagePlan("commit", StageRole.COMMIT, "create", "projects.create",
                                true, List.of("name"), List.of("createdId"), List.of("verify"),
                                ScenarioModels.VerificationType.OUTPUT_EXTRACTABLE)
                ),
                List.of("name", "createdId", "verifiedResource"), 0.8, List.of()
        );

        ScenarioCompiler.CompilationResult result = compiler.compile(List.of(shuffled), capabilities);

        assertThat(result.scenarios().get(0).entryStageId()).isEqualTo("prepare");
        assertThat(result.scenarios().get(0).status()).isEqualTo(CompilationStatus.EXECUTABLE);
    }

    @Test
    void propagatesAuthenticationOutputAndNeverInventsMissingCapability() {
        List<Capability> capabilities = sampleCapabilities();
        RuleBasedScenarioPlanner.PlanningResult planned = planner.plan(
                evidence(), null, Purpose.API_TEST, capabilities);
        ScenarioCompiler.CompilationResult compiled = compiler.compile(planned.plans(), capabilities);

        CompiledScenario auth = compiled.scenarios().stream()
                .filter(candidate -> candidate.id().equals("authenticate-and-query"))
                .findFirst().orElseThrow();
        assertThat(auth.stages()).filteredOn(stage -> stage.id().equals("authenticate"))
                .flatExtracting(stage -> stage.outputBindings())
                .anyMatch(binding -> binding.to().equals("authToken") && binding.sensitive());

        ScenarioPlan impossible = new ScenarioPlan(
                "impossible", "Impossible", "developer", "Do not invent endpoints", List.of(),
                List.of(
                        new ScenarioStagePlan("commit", StageRole.COMMIT, "missing", "projects.publish",
                                true, List.of(), List.of(), List.of("complete"), null),
                        new ScenarioStagePlan("complete", StageRole.COMPLETE, "done", null,
                                true, List.of(), List.of(), List.of(), null)
                ),
                List.of(), 0.3, List.of()
        );
        ScenarioCompiler.CompilationResult missing = compiler.compile(List.of(impossible), capabilities);

        assertThat(missing.scenarios().get(0).status()).isEqualTo(CompilationStatus.UNSUPPORTED);
        assertThat(missing.diagnostics()).anyMatch(diagnostic ->
                diagnostic.message().contains("projects.publish"));
        assertThat(missing.scenarios().get(0).stages())
                .noneMatch(stage -> "projects.publish".equals(stage.capabilityId()));
    }

    @Test
    void rejectsPathBindingWhoseScenarioStateWasNeverDeclared() {
        Capability detail = capability(
                "tenants.detail", "tenants", CapabilityType.DETAIL, "getTenant",
                "/tenants/{tenantId}", "GET", List.of(), CapabilityKind.QUERY);
        ScenarioPlan invalid = new ScenarioPlan(
                "missing-state", "Missing state", "developer", "Detect binding gap", List.of(),
                List.of(
                        new ScenarioStagePlan("inspect", StageRole.INSPECT, "inspect", detail.id(),
                                true, List.of(), List.of(), List.of("complete"), null),
                        new ScenarioStagePlan("complete", StageRole.COMPLETE, "done", null,
                                true, List.of(), List.of(), List.of(), null)
                ),
                List.of(), 0.5, List.of()
        );

        ScenarioCompiler.CompilationResult result = compiler.compile(List.of(invalid), List.of(detail));

        assertThat(result.scenarios().get(0).status()).isEqualTo(CompilationStatus.UNSUPPORTED);
        assertThat(result.diagnostics()).anyMatch(diagnostic ->
                diagnostic.message().contains("선언되지 않은 scenario state binding(tenantId)"));
    }

    @Test
    void rejectsStateChangingCommitWithoutReviewAndFollowUpVerification() {
        Capability create = capability(
                "projects.create", "projects", CapabilityType.CREATE, "createProject",
                "/projects", "POST", List.of("name"), CapabilityKind.MUTATION);
        ScenarioPlan unsafe = new ScenarioPlan(
                "unsafe-create", "Unsafe create", "developer", "Safety gate", List.of(),
                List.of(
                        new ScenarioStagePlan("prepare", StageRole.PREPARE, "prepare", null,
                                true, List.of(), List.of("name"), List.of("commit"), null),
                        new ScenarioStagePlan("commit", StageRole.COMMIT, "commit", create.id(),
                                true, List.of("name"), List.of(), List.of("complete"), null),
                        new ScenarioStagePlan("complete", StageRole.COMPLETE, "done", null,
                                true, List.of(), List.of(), List.of(), null)
                ),
                List.of("name"), 0.8, List.of()
        );

        ScenarioCompiler.CompilationResult result = compiler.compile(List.of(unsafe), List.of(create));

        assertThat(result.scenarios().get(0).status()).isEqualTo(CompilationStatus.UNSUPPORTED);
        assertThat(result.diagnostics())
                .anyMatch(diagnostic -> diagnostic.message().contains("COMMIT 이전에 REVIEW"))
                .anyMatch(diagnostic -> diagnostic.message().contains("VERIFY/TRACK stage가 없음"));
    }

    @Test
    void rejectsAiStyleOutputNameThatRuntimeCannotExtract() {
        Capability list = capability(
                "projects.list", "projects", CapabilityType.LIST, "listProjects",
                "/projects", "GET", List.of(), CapabilityKind.QUERY);
        ScenarioPlan invalidOutput = new ScenarioPlan(
                "invalid-output", "Invalid output", "developer", "Extraction gate", List.of(),
                List.of(
                        new ScenarioStagePlan("discover", StageRole.DISCOVER, "discover", list.id(),
                                true, List.of(), List.of("projectRows"), List.of("complete"),
                                ScenarioModels.VerificationType.RESPONSE_SCHEMA_VALID),
                        new ScenarioStagePlan("complete", StageRole.COMPLETE, "done", null,
                                true, List.of(), List.of(), List.of(), null)
                ),
                List.of("projectRows"), 0.8, List.of()
        );

        ScenarioCompiler.CompilationResult result = compiler.compile(List.of(invalidOutput), List.of(list));

        assertThat(result.scenarios().get(0).status()).isEqualTo(CompilationStatus.UNSUPPORTED);
        assertThat(result.diagnostics()).anyMatch(diagnostic ->
                diagnostic.message().contains("추출할 수 없는 stage output(projectRows)"));
    }

    private OpenApiEvidence evidence() {
        return new OpenApiEvidence("Project API", "1", List.of("https://api.example.com"),
                List.of(), List.of(), 0);
    }

    private List<Capability> sampleCapabilities() {
        return List.of(
                capability("auth.login", "auth", CapabilityType.LOGIN, "login", "/auth/login", "POST",
                        List.of("email", "password"), CapabilityKind.AUTH),
                capability("projects.list", "projects", CapabilityType.LIST, "listProjects", "/projects", "GET",
                        List.of(), CapabilityKind.QUERY),
                capability("projects.detail", "projects", CapabilityType.DETAIL, "getProject",
                        "/projects/{projectId}", "GET", List.of(), CapabilityKind.QUERY),
                capability("projects.create", "projects", CapabilityType.CREATE, "createProject", "/projects", "POST",
                        List.of("name"), CapabilityKind.MUTATION)
        );
    }

    private Capability capability(
            String id,
            String resource,
            CapabilityType type,
            String operationId,
            String path,
            String method,
            List<String> fields,
            CapabilityKind kind
    ) {
        return new Capability(
                id, resource, type, operationId, path, method,
                false, false, false, "HIGH", List.of("test"), fields,
                type == CapabilityType.LOGIN ? "data.accessToken" : null, null,
                type == CapabilityType.CREATE ? RiskLevel.STATE_CHANGING : RiskLevel.SAFE,
                type == CapabilityType.CREATE ? AutomationPolicy.USER_INITIATED : AutomationPolicy.AUTO_SAFE,
                type == CapabilityType.LIST ? "data" : null, null, kind, null, List.of()
        );
    }
}
