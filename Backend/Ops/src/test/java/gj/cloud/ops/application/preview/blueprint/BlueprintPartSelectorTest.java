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

    private Capability capability(String id, String resourceName, CapabilityType type) {
        return new Capability(id, resourceName, type, null, "/" + resourceName, "GET", false, false, false, "HIGH",
                List.of(), List.of(), null, null, RiskLevel.SAFE, AutomationPolicy.AUTO_SAFE, null, null,
                type == CapabilityType.LOGIN ? CapabilityKind.AUTH : CapabilityKind.QUERY, null, List.of());
    }
}
