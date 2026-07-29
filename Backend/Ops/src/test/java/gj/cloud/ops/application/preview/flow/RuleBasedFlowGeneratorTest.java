package gj.cloud.ops.application.preview.flow;

import gj.cloud.ops.application.preview.analysis.AutomationPolicy;
import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.CapabilityKind;
import gj.cloud.ops.application.preview.analysis.CapabilityType;
import gj.cloud.ops.application.preview.analysis.RiskLevel;
import gj.cloud.ops.application.preview.binding.ApiBinding;
import gj.cloud.ops.application.preview.binding.ApiBindingValidator;
import gj.cloud.ops.application.preview.planning.model.NavigationRule;
import gj.cloud.ops.application.preview.planning.model.PagePlan;
import gj.cloud.ops.application.preview.planning.model.PageType;
import gj.cloud.ops.application.preview.planning.model.RouteParameter;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// §15 수직 슬라이스 시나리오(VM 생성→선택→상태추적→시작/정지)의 생성 쪽 절반 — 이 생성기가 만든
// FlowBlueprint/ApiBinding이 실제로 FlowBlueprintValidator/ApiBindingValidator를 통과하는지가
// 핵심 회귀. RuleBasedFlowGeneratorTest는 FlowExecutorTest처럼 손으로 짠 시나리오가 아니라 "생성기가
// 시나리오를 스스로 만들어낼 수 있는가"를 확인한다.
class RuleBasedFlowGeneratorTest {

    private final RuleBasedFlowGenerator generator = new RuleBasedFlowGenerator();

    @Test
    void createAndDetailOnSamePageProducesSelfNavigatingCreateFlow() {
        Capability create = capability("vm.create", "vms", CapabilityType.CREATE, "/vms", "POST",
                List.of("name"), CapabilityKind.MUTATION, null, List.of());
        Capability detail = capability("vm.detail", "vms", CapabilityType.DETAIL, "/vms/{id}", "GET",
                List.of(), CapabilityKind.QUERY, null, List.of());
        PagePlan page = pagePlan("vm-page", List.of(create.id(), detail.id()));

        RuleBasedFlowGenerator.Result result = generator.generate(List.of(page), List.of(create, detail));

        assertThat(result.flows()).hasSize(1);
        FlowBlueprint flow = result.flows().get(0);
        assertThat(flow.trigger().pageId()).isEqualTo("vm-page");
        assertThat(flow.trigger().actionId()).isEqualTo("vm.create");
        assertThat(flow.steps()).extracting(FlowStep::type)
                .containsExactly(FlowStepType.API_CALL, FlowStepType.NAVIGATE);
        FlowStep navigate = flow.steps().get(1);
        assertThat(navigate.pageId()).isEqualTo("vm-page");
        assertThat(navigate.parameters()).containsEntry("selected", "$context.createdId");

        assertThat(FlowBlueprintValidator.validate(flow, Set.of("vm-page"))).isEmpty();
        assertThat(ApiBindingValidator.validate(result.bindings(), List.of(create, detail))).isEmpty();
    }

    @Test
    void createFlowUsesExplicitNavigationToIndependentDetailPage() {
        Capability create = capability("vm.create", "vms", CapabilityType.CREATE, "/vms", "POST",
                List.of("name"), CapabilityKind.MUTATION, null, List.of());
        Capability detail = capability("vm.detail", "vms", CapabilityType.DETAIL, "/vms/{vmId}", "GET",
                List.of(), CapabilityKind.QUERY, null, List.of());
        NavigationRule createSuccess = new NavigationRule("vm-list", "create.success",
                NavigationRule.NavigationType.OPEN_PAGE, "vm-detail", Map.of("vmId", "$row.id"));
        PagePlan listPage = new PagePlan("vm-list", "VM 목록", "/vm-list", PageType.RESOURCE_LIST,
                "resource-list-layout", List.of(create.id()), List.of(), List.of(), List.of(createSuccess),
                Map.of("quickActions", false), "HIGH", "테스트", List.of());
        PagePlan detailPage = new PagePlan("vm-detail", "VM 상세", "/vm-detail/:vmId",
                PageType.RESOURCE_DETAIL, "resource-detail-layout", List.of(detail.id()),
                List.of(new RouteParameter("vmId", "navigation")), List.of(), List.of(),
                Map.of("quickActions", false), "HIGH", "테스트", List.of());

        RuleBasedFlowGenerator.Result result = generator.generate(
                List.of(listPage, detailPage), List.of(create, detail));

        assertThat(result.flows()).hasSize(1);
        FlowBlueprint flow = result.flows().get(0);
        assertThat(flow.steps()).extracting(FlowStep::type)
                .containsExactly(FlowStepType.API_CALL, FlowStepType.NAVIGATE);
        assertThat(flow.steps().get(1).pageId()).isEqualTo("vm-detail");
        assertThat(flow.steps().get(1).parameters()).containsEntry("vmId", "$context.createdId");
        ApiBinding createBinding = result.bindings().stream()
                .filter(binding -> binding.capabilityId().equals(create.id())).findFirst().orElseThrow();
        assertThat(createBinding.outputMappings())
                .extracting(ApiBinding.OutputMapping::from)
                .containsExactly("data.id", "result.id", "payload.id", "id");
        assertThat(FlowBlueprintValidator.validate(flow, Set.of("vm-list", "vm-detail"))).isEmpty();
        assertThat(ApiBindingValidator.validate(result.bindings(), List.of(create, detail))).isEmpty();
    }

    @Test
    void createWithoutDetailOnPageProducesNoFlow() {
        Capability create = capability("vm.create", "vms", CapabilityType.CREATE, "/vms", "POST",
                List.of("name"), CapabilityKind.MUTATION, null, List.of());
        PagePlan page = pagePlan("vm-page", List.of(create.id()));

        RuleBasedFlowGenerator.Result result = generator.generate(List.of(page), List.of(create));

        assertThat(result.flows()).isEmpty();
        assertThat(result.bindings()).isEmpty();
    }

    @Test
    void commandCapabilityRefreshesItsDependencies() {
        Capability detail = capability("vm.detail", "vms", CapabilityType.DETAIL, "/vms/{id}", "GET",
                List.of(), CapabilityKind.QUERY, null, List.of());
        Capability start = capability("vm.start", "vms", null, "/vms/{id}/start", "POST",
                List.of(), CapabilityKind.COMMAND, "start", List.of(detail.id()));
        PagePlan page = pagePlan("vm-page", List.of(detail.id(), start.id()));

        RuleBasedFlowGenerator.Result result = generator.generate(List.of(page), List.of(detail, start));

        assertThat(result.flows()).hasSize(1);
        FlowBlueprint flow = result.flows().get(0);
        assertThat(flow.id()).isEqualTo("vm-page-start-flow");
        assertThat(flow.steps()).hasSize(1);
        assertThat(flow.steps().get(0).type()).isEqualTo(FlowStepType.API_CALL);

        ApiBinding commandBinding = result.bindings().stream()
                .filter(b -> b.capabilityId().equals("vm.start")).findFirst().orElseThrow();
        assertThat(commandBinding.refreshBindingIds()).containsExactly("vm.detail-binding");
        assertThat(commandBinding.inputMappings())
                .anyMatch(m -> m.target().equals("id") && m.targetKind() == ApiBinding.InputMapping.InputTarget.PATH
                        && m.from().equals("$route.selected"));

        assertThat(FlowBlueprintValidator.validate(flow, Set.of("vm-page"))).isEmpty();
        assertThat(ApiBindingValidator.validate(result.bindings(), List.of(detail, start))).isEmpty();
    }

    @Test
    void commandOnIndependentDetailUsesDeclaredRouteParameter() {
        Capability detail = capability("vm.detail", "vms", CapabilityType.DETAIL, "/vms/{vmId}", "GET",
                List.of(), CapabilityKind.QUERY, null, List.of());
        Capability start = capability("vm.start", "vms", null, "/vms/{vmId}/start", "POST",
                List.of(), CapabilityKind.COMMAND, "start", List.of(detail.id()));
        PagePlan page = new PagePlan("vm-detail", "VM 상세", "/vm-detail/:id", PageType.RESOURCE_DETAIL,
                "resource-detail-layout", List.of(detail.id(), start.id()),
                List.of(new RouteParameter("id", "navigation")), List.of(), List.of(),
                Map.of("quickActions", true), "HIGH", "테스트", List.of());

        RuleBasedFlowGenerator.Result result = generator.generate(List.of(page), List.of(detail, start));

        ApiBinding commandBinding = result.bindings().stream()
                .filter(binding -> binding.capabilityId().equals(start.id())).findFirst().orElseThrow();
        ApiBinding refreshBinding = result.bindings().stream()
                .filter(binding -> binding.capabilityId().equals(detail.id())).findFirst().orElseThrow();
        assertThat(commandBinding.inputMappings())
                .anyMatch(mapping -> mapping.target().equals("vmId") && mapping.from().equals("$route.id"));
        assertThat(refreshBinding.inputMappings())
                .anyMatch(mapping -> mapping.target().equals("vmId") && mapping.from().equals("$route.id"));
        assertThat(ApiBindingValidator.validate(result.bindings(), List.of(detail, start))).isEmpty();
    }

    @Test
    void commandWithUnknownDependencyIsSkippedGracefully() {
        Capability start = capability("vm.start", "vms", null, "/vms/{id}/start", "POST",
                List.of(), CapabilityKind.COMMAND, "start", List.of("vm.detail"));
        PagePlan page = pagePlan("vm-page", List.of(start.id()));

        RuleBasedFlowGenerator.Result result = generator.generate(List.of(page), List.of(start));

        assertThat(result.flows()).hasSize(1);
        ApiBinding commandBinding = result.bindings().get(0);
        assertThat(commandBinding.refreshBindingIds()).isEmpty();
    }

    @Test
    void multipleCommandsSharingSameDependencyReuseOneRefreshBinding() {
        Capability detail = capability("vm.detail", "vms", CapabilityType.DETAIL, "/vms/{id}", "GET",
                List.of(), CapabilityKind.QUERY, null, List.of());
        Capability start = capability("vm.start", "vms", null, "/vms/{id}/start", "POST",
                List.of(), CapabilityKind.COMMAND, "start", List.of(detail.id()));
        Capability stop = capability("vm.stop", "vms", null, "/vms/{id}/stop", "POST",
                List.of(), CapabilityKind.COMMAND, "stop", List.of(detail.id()));
        PagePlan page = pagePlan("vm-page", List.of(detail.id(), start.id(), stop.id()));

        RuleBasedFlowGenerator.Result result = generator.generate(List.of(page), List.of(detail, start, stop));

        assertThat(result.flows()).hasSize(2);
        assertThat(result.bindings()).extracting(ApiBinding::id)
                .containsExactlyInAnyOrder("vm.start-binding", "vm.stop-binding", "vm.detail-binding");
        assertThat(ApiBindingValidator.validate(result.bindings(), List.of(detail, start, stop))).isEmpty();
    }

    @Test
    void commandsWithTheSameActionStillReceiveUniqueFlowIds() {
        Capability cancelEvent = capability("events.cancel", "events", null, "/events/{id}/cancel", "POST",
                List.of(), CapabilityKind.COMMAND, "cancel", List.of());
        Capability cancelSeries = capability(
                "events.cancel-series", "events", null, "/events/{id}/series/cancel", "POST",
                List.of(), CapabilityKind.COMMAND, "cancel", List.of());
        PagePlan page = pagePlan(
                "events-page", List.of(cancelEvent.id(), cancelSeries.id()));

        RuleBasedFlowGenerator.Result result =
                generator.generate(List.of(page), List.of(cancelEvent, cancelSeries));

        assertThat(result.flows()).extracting(FlowBlueprint::id).doesNotHaveDuplicates();
        assertThat(result.flows()).extracting(flow -> flow.trigger().actionId())
                .containsExactly("events.cancel", "events.cancel-series");
        assertThat(result.flows()).allSatisfy(flow ->
                assertThat(FlowBlueprintValidator.validate(flow, Set.of("events-page"))).isEmpty());
    }

    @Test
    void createFlowInsertsBoundedPollWhenDetailHasStatusTransition() {
        Capability create = capability("machines.create", "machines", CapabilityType.CREATE, "/machines", "POST",
                List.of("name"), CapabilityKind.MUTATION, null, List.of());
        Capability detail = new Capability("machines.detail", "machines", CapabilityType.DETAIL, null,
                "/machines/{id}", "GET", false, false, false, "HIGH", List.of(), List.of(), null, null,
                RiskLevel.SAFE, AutomationPolicy.AUTO_SAFE, null, null, CapabilityKind.QUERY, null, List.of(),
                new Capability.PollHint("status", List.of("RUNNING", "STOPPED")));
        PagePlan page = pagePlan("machines-page", List.of(create.id(), detail.id()));

        RuleBasedFlowGenerator.Result result = generator.generate(List.of(page), List.of(create, detail));

        assertThat(result.flows()).hasSize(1);
        FlowBlueprint flow = result.flows().get(0);
        // POLL은 반드시 NAVIGATE 앞 — navigate가 flow를 abort시킬 수 있어서.
        assertThat(flow.steps()).extracting(FlowStep::type)
                .containsExactly(FlowStepType.API_CALL, FlowStepType.POLL, FlowStepType.NAVIGATE);
        FlowStep poll = flow.steps().get(1);
        assertThat(poll.until()).singleElement().satisfies(condition -> {
            assertThat(condition.path()).isEqualTo("status");
            assertThat(condition.equalsValue()).isNull();
            assertThat(condition.in()).containsExactly("RUNNING", "STOPPED");
        });
        assertThat(poll.intervalMs()).isEqualTo(FlowExecutionPolicy.MIN_INTERVAL_MS);
        assertThat(poll.timeoutSeconds()).isBetween(1, FlowExecutionPolicy.MAX_TIMEOUT_SECONDS);
        // 폴링 바인딩은 생성된 리소스 id를 경로에 쓴다.
        ApiBinding pollBinding = result.bindings().stream()
                .filter(binding -> binding.id().equals(poll.bindingRef())).findFirst().orElseThrow();
        assertThat(pollBinding.capabilityId()).isEqualTo("machines.detail");
        assertThat(pollBinding.inputMappings()).singleElement().satisfies(mapping -> {
            assertThat(mapping.target()).isEqualTo("id");
            assertThat(mapping.targetKind()).isEqualTo(ApiBinding.InputMapping.InputTarget.PATH);
            assertThat(mapping.from()).isEqualTo("$context.createdId");
        });
        assertThat(FlowBlueprintValidator.validate(flow, Set.of("machines-page"))).isEmpty();
        assertThat(ApiBindingValidator.validate(result.bindings(), List.of(create, detail))).isEmpty();
    }

    @Test
    void createFlowSkipsPollWhenDetailHasNoStatusTransition() {
        Capability create = capability("vm.create", "vms", CapabilityType.CREATE, "/vms", "POST",
                List.of("name"), CapabilityKind.MUTATION, null, List.of());
        Capability detail = capability("vm.detail", "vms", CapabilityType.DETAIL, "/vms/{id}", "GET",
                List.of(), CapabilityKind.QUERY, null, List.of());
        PagePlan page = pagePlan("vm-page", List.of(create.id(), detail.id()));

        RuleBasedFlowGenerator.Result result = generator.generate(List.of(page), List.of(create, detail));

        assertThat(result.flows().get(0).steps()).extracting(FlowStep::type)
                .containsExactly(FlowStepType.API_CALL, FlowStepType.NAVIGATE);
    }

    private PagePlan pagePlan(String id, List<String> capabilityIds) {
        Map<String, Boolean> features = new LinkedHashMap<>();
        features.put("quickActions", false);
        features.put("statusSummary", false);
        features.put("childResourceTabs", false);
        return new PagePlan(id, id, "/" + id, PageType.LIST_DETAIL, null, capabilityIds,
                List.of(), List.of(), List.of(), features, "HIGH", "테스트용", List.of());
    }

    private Capability capability(String id, String resourceName, CapabilityType type, String path, String method,
                                   List<String> fields, CapabilityKind kind, String action, List<String> dependencies) {
        return new Capability(id, resourceName, type, null, path, method, false, false, false, "HIGH",
                List.of(), fields, null, null, RiskLevel.SAFE, AutomationPolicy.AUTO_SAFE, null, null,
                kind, action, dependencies);
    }
}
