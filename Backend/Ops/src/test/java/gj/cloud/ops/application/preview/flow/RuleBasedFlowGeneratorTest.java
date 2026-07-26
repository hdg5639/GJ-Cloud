package gj.cloud.ops.application.preview.flow;

import gj.cloud.ops.application.preview.analysis.AutomationPolicy;
import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.CapabilityKind;
import gj.cloud.ops.application.preview.analysis.CapabilityType;
import gj.cloud.ops.application.preview.analysis.RiskLevel;
import gj.cloud.ops.application.preview.binding.ApiBinding;
import gj.cloud.ops.application.preview.binding.ApiBindingValidator;
import gj.cloud.ops.application.preview.planning.model.PagePlan;
import gj.cloud.ops.application.preview.planning.model.PageType;
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
