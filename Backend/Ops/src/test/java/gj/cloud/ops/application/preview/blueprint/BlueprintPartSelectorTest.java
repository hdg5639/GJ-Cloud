package gj.cloud.ops.application.preview.blueprint;

import gj.cloud.ops.application.preview.analysis.AutomationPolicy;
import gj.cloud.ops.application.preview.analysis.Block;
import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.CapabilityKind;
import gj.cloud.ops.application.preview.analysis.CapabilityType;
import gj.cloud.ops.application.preview.analysis.RiskLevel;
import gj.cloud.ops.application.preview.dto.PreviewAnalyzeRequest.Purpose;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BlueprintPartSelectorTest {

    @Test
    void swapsInfrastructureDetailForFullDetailPage() {
        Capability detail = capability("machines.detail", "machines", CapabilityType.DETAIL);
        Map<String, List<Block>> blocks = Map.of("machines-page", List.of(
                new Block("detail", "full-detail-page", "page.main", List.of("machines.detail"), null)));

        Map<String, List<Block>> result = BlueprintPartSelector.select(blocks, List.of(detail), Purpose.PRODUCT_LIKE);

        assertThat(result.get("machines-page").get(0).componentId()).isEqualTo("infrastructure-resource-detail");
    }

    @Test
    void swapsStandaloneInfrastructureDetailOnPrimarySurface() {
        Capability detail = capability("machines.detail", "machines", CapabilityType.DETAIL);
        Map<String, List<Block>> blocks = Map.of("machines-page", List.of(
                new Block("detail", "full-detail-page", "page.primary", List.of("machines.detail"), null)));

        Map<String, List<Block>> result = BlueprintPartSelector.select(blocks, List.of(detail), Purpose.PRODUCT_LIKE);

        assertThat(result.get("machines-page").get(0).componentId()).isEqualTo("infrastructure-resource-detail");
    }

    @Test
    void overlaySelectionRespectsCreateAndDeleteModes() {
        Capability create = capability("machines.create", "machines", CapabilityType.CREATE);
        Capability delete = capability("machines.delete", "machines", CapabilityType.DELETE);
        Map<String, List<Block>> blocks = Map.of("machines-page", List.of(
                new Block("create", "create-edit-modal", "page.overlay", List.of(create.id()), "CREATE"),
                new Block("delete", "delete-confirm-modal", "page.overlay", List.of(delete.id()), "DELETE")));

        Map<String, List<Block>> result = BlueprintPartSelector.select(
                blocks, List.of(create, delete), Purpose.PRODUCT_LIKE);

        assertThat(result.get("machines-page"))
                .extracting(Block::componentId)
                .containsExactly("resource-provisioning-wizard", "dependency-impact-modal");
    }

    @Test
    void swapsCrmListForEntityDirectory() {
        Capability list = capability("customers.list", "customers", CapabilityType.LIST);
        Map<String, List<Block>> blocks = Map.of("customers-page", List.of(
                new Block("list", "resource-card-grid", "page.main", List.of("customers.list"), null)));

        Map<String, List<Block>> result = BlueprintPartSelector.select(blocks, List.of(list), Purpose.PRODUCT_LIKE);

        assertThat(result.get("customers-page").get(0).componentId()).isEqualTo("entity-directory");
    }

    @Test
    void keepsBaseComponentWhenCategoryIsUnknown() {
        // "todos"는 PROJECT로 분류되지만, "widgets"처럼 사전에 없는 리소스는 분류 불가 → 기본 유지.
        Capability list = capability("widgets.list", "widgets", CapabilityType.LIST);
        Map<String, List<Block>> blocks = Map.of("widgets-page", List.of(
                new Block("list", "resource-card-grid", "page.main", List.of("widgets.list"), null)));

        Map<String, List<Block>> result = BlueprintPartSelector.select(blocks, List.of(list), Purpose.PRODUCT_LIKE);

        assertThat(result.get("widgets-page").get(0).componentId()).isEqualTo("resource-card-grid");
    }

    @Test
    void keepsNonSwapComponents() {
        Capability login = capability("auth.login", "auth", CapabilityType.LOGIN);
        Map<String, List<Block>> blocks = Map.of("auth-page", List.of(
                new Block("login", "login-form", "page.content", List.of("auth.login"), null)));

        Map<String, List<Block>> result = BlueprintPartSelector.select(blocks, List.of(login), Purpose.ADMIN);

        assertThat(result.get("auth-page").get(0).componentId()).isEqualTo("login-form");
    }

    @Test
    void keepsBaseWhenNoPartMatchesSlot() {
        // infrastructure-resource-detail은 page.main만 받는다 — detail-panel(page.aside)은 대상이 아니고,
        // 애초에 detail-panel은 kindOfBaseComponent 대상도 아니라 그대로 유지된다.
        Capability detail = capability("machines.detail", "machines", CapabilityType.DETAIL);
        Map<String, List<Block>> blocks = Map.of("machines-page", List.of(
                new Block("detail", "detail-panel", "page.aside", List.of("machines.detail"), null)));

        Map<String, List<Block>> result = BlueprintPartSelector.select(blocks, List.of(detail), Purpose.ADMIN);

        assertThat(result.get("machines-page").get(0).componentId()).isEqualTo("detail-panel");
    }

    @Test
    void overrideForcesCompatiblePartRegardlessOfCategory() {
        // widgets는 카테고리 미분류(자동선택이면 기본 유지)지만, 사용자가 kanban을 강제하면 반영된다.
        Capability list = capability("widgets.list", "widgets", CapabilityType.LIST);
        Map<String, List<Block>> blocks = Map.of("widgets-page", List.of(
                new Block("list", "resource-card-grid", "page.main", List.of("widgets.list"), null)));

        Map<String, List<Block>> result = BlueprintPartSelector.select(blocks, List.of(list),
                Purpose.PRODUCT_LIKE, Map.of("widgets-page/list", "kanban-collection"));

        assertThat(result.get("widgets-page").get(0).componentId()).isEqualTo("kanban-collection");
    }

    @Test
    void overrideToBaseComponentKeepsBaseEvenWhenCategoryMatches() {
        // customers는 자동이면 entity-directory지만, 사용자가 기본(resource-card-grid) 지정 시 유지.
        Capability list = capability("customers.list", "customers", CapabilityType.LIST);
        Map<String, List<Block>> blocks = Map.of("customers-page", List.of(
                new Block("list", "resource-card-grid", "page.main", List.of("customers.list"), null)));

        Map<String, List<Block>> result = BlueprintPartSelector.select(blocks, List.of(list),
                Purpose.PRODUCT_LIKE, Map.of("customers-page/list", "resource-card-grid"));

        assertThat(result.get("customers-page").get(0).componentId()).isEqualTo("resource-card-grid");
    }

    @Test
    void incompatibleOverrideFallsBackToAutoSelect() {
        // dashboard 파츠를 list Block에 지정 = 비호환 → 무시하고 자동선택(entity-directory).
        Capability list = capability("customers.list", "customers", CapabilityType.LIST);
        Map<String, List<Block>> blocks = Map.of("customers-page", List.of(
                new Block("list", "resource-card-grid", "page.main", List.of("customers.list"), null)));

        Map<String, List<Block>> result = BlueprintPartSelector.select(blocks, List.of(list),
                Purpose.PRODUCT_LIKE, Map.of("customers-page/list", "operations-health-dashboard"));

        assertThat(result.get("customers-page").get(0).componentId()).isEqualTo("entity-directory");
    }

    @Test
    void swapsExpansionPackFinanceDashboardForNewCategory() {
        // Expansion Pack — "transactions"는 신규 FINANCE 카테고리로 분류되고, FINANCE DASHBOARD 파츠
        // (finance-cashflow-dashboard, 해당 kind/category 유일)로 치환된다. 단, 확장 팩 파츠는
        // preferredPurposes가 ADMIN/PRODUCT_LIKE라 그 purpose에서만 자동선택된다.
        Capability list = capability("transactions.list", "transactions", CapabilityType.LIST);
        Map<String, List<Block>> blocks = Map.of("finance-page", List.of(
                new Block("dash", "dashboard-view", "page.content", List.of("transactions.list"), null)));

        Map<String, List<Block>> result = BlueprintPartSelector.select(blocks, List.of(list), Purpose.ADMIN);

        assertThat(result.get("finance-page").get(0).componentId()).isEqualTo("finance-cashflow-dashboard");
    }

    @Test
    void expansionPackPartIsNotAutoSelectedForApiTestPurpose() {
        // 확장 팩 파츠는 preferredPurposes=ADMIN/PRODUCT_LIKE이므로 API_TEST에서는 자동선택되지 않고
        // 기본 컴포넌트가 유지된다.
        Capability list = capability("transactions.list", "transactions", CapabilityType.LIST);
        Map<String, List<Block>> blocks = Map.of("finance-page", List.of(
                new Block("dash", "dashboard-view", "page.content", List.of("transactions.list"), null)));

        Map<String, List<Block>> result = BlueprintPartSelector.select(blocks, List.of(list), Purpose.API_TEST);

        assertThat(result.get("finance-page").get(0).componentId()).isEqualTo("dashboard-view");
    }

    private Capability capability(String id, String resourceName, CapabilityType type) {
        return new Capability(id, resourceName, type, null, "/" + resourceName, "GET", false, false, false, "HIGH",
                List.of(), List.of(), null, null, RiskLevel.SAFE, AutomationPolicy.AUTO_SAFE, null, null,
                type == CapabilityType.LOGIN ? CapabilityKind.AUTH : CapabilityKind.QUERY, null, List.of());
    }
}
