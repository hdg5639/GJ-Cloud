package gj.cloud.ops.application.preview.planning.patch;

import gj.cloud.ops.application.preview.ai.PagePlanOperation;
import gj.cloud.ops.application.preview.ai.PagePlanOperationType;
import gj.cloud.ops.application.preview.analysis.AutomationPolicy;
import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.CapabilityKind;
import gj.cloud.ops.application.preview.analysis.CapabilityType;
import gj.cloud.ops.application.preview.analysis.RiskLevel;
import gj.cloud.ops.application.preview.binding.ApiBinding;
import gj.cloud.ops.application.preview.flow.FlowBlueprint;
import gj.cloud.ops.application.preview.flow.FlowStep;
import gj.cloud.ops.application.preview.flow.FlowStepType;
import gj.cloud.ops.application.preview.planning.model.NavigationRule;
import gj.cloud.ops.application.preview.planning.model.PagePlan;
import gj.cloud.ops.application.preview.planning.model.PageType;
import gj.cloud.ops.application.preview.planning.model.RouteParameter;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// WP-4 회귀 — operation enum 존재 여부가 아니라, 여러 operation이 순서대로 적용된 최종
// PagePlan/Flow/Binding 정본이 실제 배포 Hard Gate를 통과하거나 올바르게 거절되는지 확인한다.
class PagePlanPatchValidatorTest {

    @Test
    void splitDetailAndAddNavigationProduceRoutableIndependentDetailPage() {
        Capability list = capability("vm.list", CapabilityType.LIST, "/vms", "GET");
        Capability detail = capability("vm.detail", CapabilityType.DETAIL, "/vms/{id}", "GET");
        Capability create = capability("vm.create", CapabilityType.CREATE, "/vms", "POST");
        PagePlan source = page("vm-page", PageType.LIST_DETAIL, "list-detail-layout",
                List.of(list.id(), detail.id(), create.id()));
        PlanPatchState initial = new PlanPatchState(List.of(source), List.of(), List.of());

        PagePlanOperation split = operation(PagePlanOperationType.SPLIT_PAGE,
                "vm-page", null, "VM 상세", null, "vm-detail", List.of(detail.id()),
                PageType.RESOURCE_DETAIL, null, null, null, null, null, null, null,
                "목록과 상세 사용자 흐름을 분리");
        NavigationRule navigation = new NavigationRule(
                "vm-page", "row.select", NavigationRule.NavigationType.OPEN_PAGE,
                "vm-detail", Map.of("id", "$row.id"));
        PagePlanOperation addNavigation = operation(PagePlanOperationType.ADD_NAVIGATION,
                null, null, null, null, null, null, null, null, null, null,
                navigation, null, null, null, "행 선택 시 상세로 이동");

        PagePlanPatchApplyResult result = PagePlanPatchValidator.apply(
                initial, List.of(list, detail, create), List.of(split, addNavigation));

        assertThat(result.errors()).isEmpty();
        PagePlan listPage = result.state().pagePlans().stream()
                .filter(page -> page.id().equals("vm-page")).findFirst().orElseThrow();
        PagePlan detailPage = result.state().pagePlans().stream()
                .filter(page -> page.id().equals("vm-detail")).findFirst().orElseThrow();

        assertThat(listPage.pageType()).isEqualTo(PageType.RESOURCE_LIST);
        assertThat(listPage.layoutRef()).isEqualTo("resource-list-layout");
        assertThat(listPage.capabilityIds()).containsExactly(list.id(), create.id());
        assertThat(listPage.navigationRules()).containsExactly(navigation);
        assertThat(detailPage.pageType()).isEqualTo(PageType.RESOURCE_DETAIL);
        assertThat(detailPage.layoutRef()).isEqualTo("resource-detail-layout");
        assertThat(detailPage.route()).isEqualTo("/vm-detail/:id");
        assertThat(detailPage.routeParameters()).extracting(parameter -> parameter.name()).containsExactly("id");
        assertThat(detailPage.capabilityIds()).containsExactly(detail.id());
    }

    @Test
    void emptyAddedPageIsAllowedDuringProposalButRejectedAsFinalState() {
        Capability list = capability("vm.list", CapabilityType.LIST, "/vms", "GET");
        PlanPatchState initial = new PlanPatchState(
                List.of(page("vm-page", PageType.RESOURCE_LIST, "resource-list-layout", List.of(list.id()))),
                List.of(), List.of());
        PagePlanOperation addPage = operation(PagePlanOperationType.ADD_PAGE,
                "empty-page", null, "빈 페이지", null, null, null,
                PageType.RESOURCE_LIST, null, null, null, null, null, null, null, "후속 이동 대상");

        PagePlanPatchApplyResult proposalPreview =
                PagePlanPatchValidator.previewOperation(initial, List.of(list), addPage);
        PagePlanPatchApplyResult finalApply =
                PagePlanPatchValidator.apply(initial, List.of(list), List.of(addPage));

        assertThat(proposalPreview.errors()).isEmpty();
        assertThat(finalApply.errors()).anyMatch(error -> error.contains("capability가 하나 이상 필요"));
        assertThat(finalApply.state()).isEqualTo(initial);
    }

    @Test
    void routePlaceholderWithoutDeclaredParameterIsRejected() {
        Capability detail = capability("vm.detail", CapabilityType.DETAIL, "/vms/{id}", "GET");
        PagePlan invalid = new PagePlan("vm-detail", "VM 상세", "/vm-detail/:vmId",
                PageType.RESOURCE_DETAIL, "resource-detail-layout", List.of(detail.id()),
                List.of(new RouteParameter("id", "navigation")), List.of(), List.of(),
                Map.of("quickActions", false), "HIGH", "테스트", List.of());

        List<String> errors = PagePlanPatchValidator.validateFinal(
                new PlanPatchState(List.of(invalid), List.of(), List.of()), List.of(detail));

        assertThat(errors).anyMatch(error -> error.contains("route placeholder에 대응하는 RouteParameter가 없음(vmId)"));
        assertThat(errors).anyMatch(error -> error.contains("route에 선언되지 않은 route parameter(id)"));
    }

    @Test
    void addFlowThenAssignFlowPreservesUserDefinedWorkflow() {
        Capability start = command("vm.start", "/vms/{id}/start");
        PagePlan page = page("vm-page", PageType.RESOURCE_LIST, "resource-list-layout", List.of(start.id()));
        ApiBinding binding = new ApiBinding("vm-start-binding", start.id(),
                List.of(new ApiBinding.InputMapping("id", ApiBinding.InputMapping.InputTarget.PATH,
                        "$route.selected")), List.of(), List.of());
        FlowStep call = new FlowStep("run-start", FlowStepType.API_CALL, binding.id(),
                null, null, null, null, null, null, null, null, null);
        FlowBlueprint unassigned = new FlowBlueprint("vm-start-flow", null, List.of(call));
        PlanPatchState initial = new PlanPatchState(List.of(page), List.of(), List.of(binding));

        PagePlanOperation addFlow = operation(PagePlanOperationType.ADD_FLOW,
                null, null, null, null, null, null, null, null, null, null,
                null, unassigned, null, null, "시작 명령 Flow 추가");
        PagePlanOperation assign = operation(PagePlanOperationType.ASSIGN_FLOW,
                "vm-page", null, null, null, null, null, null, null, null, null,
                null, null, "vm-start-flow", start.id(), "VM 시작 버튼에 연결");

        PagePlanPatchApplyResult result = PagePlanPatchValidator.apply(
                initial, List.of(start), List.of(addFlow, assign));

        assertThat(result.errors()).isEmpty();
        assertThat(result.state().flows()).singleElement().satisfies(flow -> {
            assertThat(flow.id()).isEqualTo("vm-start-flow");
            assertThat(flow.trigger().pageId()).isEqualTo("vm-page");
            assertThat(flow.trigger().actionId()).isEqualTo(start.id());
        });
    }

    @Test
    void destructiveCapabilityCannotRunAsAutomaticFollowUpStep() {
        Capability start = command("vm.start", "/vms/{id}/start");
        Capability delete = new Capability("vm.delete", "vm", CapabilityType.DELETE, null,
                "/vms/{id}", "DELETE", false, false, false, "HIGH", List.of(), List.of(),
                null, null, RiskLevel.DESTRUCTIVE, AutomationPolicy.TYPED_CONFIRMATION,
                null, null, CapabilityKind.MUTATION, null, List.of());
        PagePlan page = page("vm-page", PageType.RESOURCE_LIST, "resource-list-layout",
                List.of(start.id(), delete.id()));
        ApiBinding startBinding = new ApiBinding("vm-start-binding", start.id(),
                List.of(new ApiBinding.InputMapping("id", ApiBinding.InputMapping.InputTarget.PATH,
                        "$route.selected")), List.of(), List.of());
        ApiBinding deleteBinding = new ApiBinding("vm-delete-binding", delete.id(),
                List.of(new ApiBinding.InputMapping("id", ApiBinding.InputMapping.InputTarget.PATH,
                        "$route.selected")), List.of(), List.of());
        FlowBlueprint flow = new FlowBlueprint("unsafe-flow",
                new FlowBlueprint.FlowTrigger(page.id(), start.id()), List.of(
                new FlowStep("start", FlowStepType.API_CALL, startBinding.id(), null, null,
                        null, null, null, null, null, null, null),
                new FlowStep("delete-after-start", FlowStepType.API_CALL, deleteBinding.id(), null, null,
                        null, null, null, null, null, null, null)));

        List<String> errors = PagePlanPatchValidator.validateFinal(
                new PlanPatchState(List.of(page), List.of(flow), List.of(startBinding, deleteBinding)),
                List.of(start, delete));

        assertThat(errors).anyMatch(error -> error.contains("자동 후속 step") && error.contains("vm.delete"));
    }

    @Test
    void malformedExternalStateIsRejectedWithoutThrowing() {
        Capability list = capability("vm.list", CapabilityType.LIST, "/vms", "GET");
        PlanPatchState malformed = new PlanPatchState(
                java.util.Arrays.asList((PagePlan) null), List.of(), List.of());

        PagePlanPatchApplyResult result = PagePlanPatchValidator.apply(malformed, List.of(list), List.of());

        assertThat(result.errors()).contains("기존 상태에 null PagePlan이 있음");
        assertThat(result.state()).isEqualTo(malformed);
    }

    @Test
    void duplicateCapabilityIdsAndNullNavigationAreReportedByHardGate() {
        Capability first = capability("vm.list", CapabilityType.LIST, "/vms", "GET");
        Capability duplicate = capability("vm.list", CapabilityType.LIST, "/other-vms", "GET");
        PagePlan page = new PagePlan("vm-page", "VM", "/vm-page", PageType.RESOURCE_LIST,
                "resource-list-layout", List.of(first.id()), List.of(), List.of(),
                java.util.Arrays.asList((NavigationRule) null), Map.of(), "HIGH", "테스트", List.of());

        List<String> errors = PagePlanPatchValidator.validateFinal(
                new PlanPatchState(List.of(page), List.of(), List.of()), List.of(first, duplicate));

        assertThat(errors).anyMatch(error -> error.contains("중복된 capability id: vm.list"));
        assertThat(errors).anyMatch(error -> error.contains("null NavigationRule"));
    }

    @Test
    void duplicateNavigationTriggersAndFlowAssignmentsAreRejected() {
        Capability list = capability("vm.list", CapabilityType.LIST, "/vms", "GET");
        Capability create = capability("vm.create", CapabilityType.CREATE, "/vms", "POST");
        NavigationRule firstNavigation = new NavigationRule(
                "vm-page", "row.select", NavigationRule.NavigationType.OPEN_PAGE, "vm-page", Map.of());
        NavigationRule duplicateNavigation = new NavigationRule(
                "vm-page", "row.select", NavigationRule.NavigationType.REPLACE_ROUTE, "vm-page", Map.of());
        PagePlan page = new PagePlan("vm-page", "VM", "/vm-page", PageType.RESOURCE_LIST,
                "resource-list-layout", List.of(list.id(), create.id()), List.of(), List.of(),
                List.of(firstNavigation, duplicateNavigation), Map.of(), "HIGH", "테스트", List.of());
        ApiBinding binding = new ApiBinding("vm-create-binding", create.id(), List.of(), List.of(), List.of());
        FlowStep call = new FlowStep("create", FlowStepType.API_CALL, binding.id(), null, null,
                null, null, null, null, null, null, null);
        FlowBlueprint firstFlow = new FlowBlueprint("create-flow-a",
                new FlowBlueprint.FlowTrigger(page.id(), create.id()), List.of(call));
        FlowBlueprint duplicateFlow = new FlowBlueprint("create-flow-b",
                new FlowBlueprint.FlowTrigger(page.id(), create.id()), List.of(call));

        List<String> errors = PagePlanPatchValidator.validateFinal(
                new PlanPatchState(List.of(page), List.of(firstFlow, duplicateFlow), List.of(binding)),
                List.of(list, create));

        assertThat(errors).anyMatch(error -> error.contains("navigation rule이 중복"));
        assertThat(errors).anyMatch(error -> error.contains("같은 페이지 액션에 여러 Flow"));
    }

    @Test
    void unassignedFlowCannotReachDeployableState() {
        Capability start = command("vm.start", "/vms/{id}/start");
        PagePlan page = page("vm-page", PageType.RESOURCE_LIST, "resource-list-layout", List.of(start.id()));
        ApiBinding binding = new ApiBinding("vm-start-binding", start.id(), List.of(), List.of(), List.of());
        FlowBlueprint unassigned = new FlowBlueprint("vm-start-flow", null, List.of(
                new FlowStep("run", FlowStepType.API_CALL, binding.id(), null, null,
                        null, null, null, null, null, null, null)));
        PagePlanOperation addFlow = operation(PagePlanOperationType.ADD_FLOW,
                null, null, null, null, null, null, null, null, null, null,
                null, unassigned, null, null, "Flow 추가");

        PagePlanPatchApplyResult result = PagePlanPatchValidator.apply(
                new PlanPatchState(List.of(page), List.of(), List.of(binding)),
                List.of(start), List.of(addFlow));

        assertThat(result.errors()).anyMatch(error -> error.contains("ASSIGN_FLOW 필요"));
    }

    private PagePlan page(String id, PageType type, String layout, List<String> capabilityIds) {
        Map<String, Boolean> features = new LinkedHashMap<>();
        features.put("quickActions", false);
        features.put("statusSummary", false);
        features.put("childResourceTabs", false);
        return new PagePlan(id, id, "/" + id, type, layout, capabilityIds,
                List.of(), List.of(), List.of(), features, "HIGH", "테스트", List.of());
    }

    private Capability capability(String id, CapabilityType type, String path, String method) {
        CapabilityKind kind = type == CapabilityType.LIST || type == CapabilityType.DETAIL
                ? CapabilityKind.QUERY : CapabilityKind.MUTATION;
        return new Capability(id, "vm", type, null, path, method, false, false, false, "HIGH",
                List.of(), List.of(), null, null, RiskLevel.SAFE, AutomationPolicy.AUTO_SAFE,
                null, null, kind, null, List.of());
    }

    private Capability command(String id, String path) {
        return new Capability(id, "vm", null, null, path, "POST", false, false, false, "HIGH",
                List.of(), List.of(), null, null, RiskLevel.STATE_CHANGING,
                AutomationPolicy.USER_INITIATED, null, null, CapabilityKind.COMMAND, "start", List.of());
    }

    private PagePlanOperation operation(
            PagePlanOperationType type, String pageId, String otherPageId, String newTitle,
            String capabilityId, String destinationPageId, List<String> capabilityIds,
            PageType pageType, String layoutRef, String featureKey, Boolean featureEnabled,
            NavigationRule navigationRule, FlowBlueprint flow, String flowId, String actionId, String reason
    ) {
        return new PagePlanOperation(type, pageId, otherPageId, newTitle, capabilityId, destinationPageId,
                capabilityIds, pageType, layoutRef, featureKey, featureEnabled, navigationRule,
                flow, flowId, actionId, reason);
    }
}
