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
    void preservesUserActionLabelAndOptionalBodyContract() {
        var schema = new gj.cloud.ops.application.preview.analysis.InputSchema("object", null, null, null,
                List.of("name"), java.util.Map.of(), null, List.of(), null, null);
        var create = capability("projects.create", "projects", CapabilityType.CREATE, "createProject",
                "/projects", "POST", List.of("name", "note"), CapabilityKind.MUTATION).withInputSchema(schema);
        var plan = new ScenarioPlan("create-project", "프로젝트 생성", "사용자", "프로젝트 생성", List.of(),
                List.of(
                        new ScenarioStagePlan("prepare", StageRole.PREPARE, "입력", null, true,
                                List.of(), List.of("name", "note"), List.of("review"), null),
                        new ScenarioStagePlan("review", StageRole.REVIEW, "확인", null, true,
                                List.of("name", "note"), List.of(), List.of("commit"), null),
                        new ScenarioStagePlan("commit", StageRole.COMMIT, "생성", create.id(), true,
                                List.of("name", "note"), List.of("createdId"), List.of("done"), null, "프로젝트 만들기"),
                        new ScenarioStagePlan("done", StageRole.COMPLETE, "완료", null, true,
                                List.of("createdId"), List.of(), List.of(), null)),
                List.of("name", "note", "createdId"), 0.9, List.of());
        var result = compiler.compile(List.of(plan), List.of(create));
        var commit = result.scenarios().get(0).stages().stream()
                .filter(stage -> stage.role() == StageRole.COMMIT).findFirst().orElseThrow();
        assertThat(commit.actionLabel()).isEqualTo("프로젝트 만들기");
        assertThat(commit.inputBindings()).anySatisfy(binding -> {
            assertThat(binding.target()).isEqualTo("name");
            assertThat(binding.required()).isTrue();
        }).anySatisfy(binding -> {
            assertThat(binding.target()).isEqualTo("note");
            assertThat(binding.required()).isFalse();
        });
    }

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

    @Test
    void connectsSelectedProductAliasToBodyWithoutReplacingCartId() {
        var result = compileBodyAliasPlan("products", false);
        var scenario = result.scenarios().get(0);
        assertThat(scenario.status()).as("%s", result.diagnostics()).isEqualTo(CompilationStatus.EXECUTABLE);
        var add = scenario.stages().stream().filter(stage -> stage.id().equals("add")).findFirst().orElseThrow();
        assertThat(add.inputBindings()).anySatisfy(binding -> {
            assertThat(binding.target()).isEqualTo("productId");
            assertThat(binding.source()).isEqualTo("$scenario.selectedId");
        }).anySatisfy(binding -> {
            assertThat(binding.target()).isEqualTo("cartId");
            assertThat(binding.source()).isEqualTo("$scenario.cartId");
        });
    }

    @Test
    void doesNotUseCartSelectionAsProductIdentifier() {
        var result = compileBodyAliasPlan("carts", false);
        assertThat(result.scenarios().get(0).status()).isEqualTo(CompilationStatus.UNSUPPORTED);
        assertThat(result.diagnostics()).anyMatch(diagnostic -> diagnostic.message().contains("productId"));
    }

    @Test
    void rejectsAmbiguousProductAliases() {
        var result = compileBodyAliasPlan("products", true);
        assertThat(result.scenarios().get(0).status()).isEqualTo(CompilationStatus.UNSUPPORTED);
        assertThat(result.diagnostics()).anyMatch(diagnostic -> diagnostic.message().contains("productId"));
    }

    private ScenarioCompiler.CompilationResult compileBodyAliasPlan(String selectedResource, boolean ambiguous) {
        Capability list = capability("source.list", selectedResource, CapabilityType.LIST, "listResources",
                "/" + selectedResource, "GET", List.of(), CapabilityKind.QUERY);
        Capability add = new Capability("carts.items", "carts", null, "addCartItem", "/carts/{cartId}/items", "POST",
                false, false, false, "HIGH", List.of("test"), List.of("productId", "variantCode", "quantity"),
                null, null, RiskLevel.STATE_CHANGING, AutomationPolicy.USER_INITIATED, null, null,
                CapabilityKind.COMMAND, "items", List.of());
        Capability order = capability("orders.create", "orders", CapabilityType.CREATE, "placeOrder", "/orders", "POST",
                List.of("cartId", "customerName", "email", "shippingAddress"), CapabilityKind.MUTATION);
        Capability detail = capability("orders.detail", "orders", CapabilityType.DETAIL, "getOrder", "/orders/{orderId}", "GET",
                List.of(), CapabilityKind.QUERY);
        var stages = new java.util.ArrayList<ScenarioStagePlan>();
        stages.add(new ScenarioStagePlan("list", StageRole.DISCOVER, "목록", list.id(), true,
                List.of(), List.of("collection"), List.of("select"), null));
        stages.add(new ScenarioStagePlan("select", StageRole.SELECT, "선택", null, true,
                List.of("collection"), List.of("selectedId"), List.of(ambiguous ? "other" : "prepare"), null));
        if (ambiguous) stages.add(new ScenarioStagePlan("other", StageRole.SELECT, "다른 선택", null, true,
                List.of("collection"), List.of("targetId"), List.of("prepare"), null));
        stages.add(new ScenarioStagePlan("prepare", StageRole.PREPARE, "기존 장바구니와 주문 정보", null, true,
                List.of(), List.of("cartId", "variantCode", "quantity", "customerName", "email", "shippingAddress"), List.of("review"), null));
        stages.add(new ScenarioStagePlan("review", StageRole.REVIEW, "확인", null, true,
                List.of(), List.of(), List.of("add"), null));
        stages.add(new ScenarioStagePlan("add", StageRole.COMMIT, "상품 담기", add.id(), true,
                ambiguous ? List.of("selectedId", "targetId", "cartId", "variantCode", "quantity")
                        : List.of("selectedId", "cartId", "variantCode", "quantity"), List.of(), List.of("order"), null));
        stages.add(new ScenarioStagePlan("order", StageRole.COMMIT, "주문", order.id(), true,
                List.of("cartId", "customerName", "email", "shippingAddress"), List.of("createdId"), List.of("verify"), null));
        stages.add(new ScenarioStagePlan("verify", StageRole.VERIFY, "주문 조회", detail.id(), true,
                List.of("createdId"), List.of("verifiedResource"), List.of("done"), ScenarioModels.VerificationType.RESOURCE_EXISTS));
        stages.add(new ScenarioStagePlan("done", StageRole.COMPLETE, "완료", null, true,
                List.of("verifiedResource"), List.of(), List.of(), null));
        var state = new java.util.ArrayList<>(List.of("collection", "selectedId", "cartId", "variantCode", "quantity",
                "customerName", "email", "shippingAddress", "createdId", "verifiedResource"));
        if (ambiguous) state.add("targetId");
        // Deliberately shuffled: lineage must follow edges, not provider array order.
        java.util.Collections.reverse(stages);
        var plan = new ScenarioPlan("body-alias", "구매", "사용자", "선택 상품으로 주문", List.of(), stages, state, 0.9, List.of());
        return compiler.compile(List.of(plan), List.of(list, add, order, detail));
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
