package gj.cloud.ops.application.preview.blueprint;

import gj.cloud.ops.application.preview.analysis.Block;
import gj.cloud.ops.application.preview.dto.PreviewAnalyzeRequest.Purpose;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Direction Recovery Change Request §11 — PagePlan(어떤 Capability가 어느 페이지에 속하는지)과 별개로,
// 이미 정해진 Block 목록에서 실제로 어떤 Component Variant를 마운트할지 purpose 기준으로 고른다.
// 계열(같은 Slot·Capability 요구조건을 공유하는 Variant 묶음)마다 기본 componentId를 키로 한
// 명시적 매핑이면 지금은 충분하다 — Variant 후보가 계열당 여러 개로 늘거나 risk 같은 다른 축까지
// 봐야 하면 그때 Component Contract에 family/preferredPurposes 같은 일반화된 검색 필드를
// 추가한다(§10.2 Retrieval order).
public final class BlueprintCompiler {

    // key: 계열의 기본(fallback) componentId, value: 그 계열에서 특정 purpose가 선호하는 Variant.
    private static final Map<String, Map<Purpose, String>> VARIANT_BY_PURPOSE = Map.of(
            "resource-table", Map.of(Purpose.PRODUCT_LIKE, "resource-card-grid"),
            // Change Request §3 "Administrator purpose — Destructive-operation safeguards".
            "delete-confirm-modal", Map.of(Purpose.ADMIN, "typed-confirm-modal"),
            // Change Request §3 "Product-like purpose — ... drawers and guided creation flows".
            "create-edit-modal", Map.of(Purpose.PRODUCT_LIKE, "form-drawer"),
            // §9.5 dashboard 계열 두 번째 Variant — PRODUCT_LIKE는 개수 카드보다 최근 항목 피드를 선호.
            "dashboard-view", Map.of(Purpose.PRODUCT_LIKE, "recent-activity-dashboard"),
            // §9.2 detail 계열 두 번째 Variant — PRODUCT_LIKE는 사이드 패널보다 전체 페이지 상세를 선호.
            "detail-panel", Map.of(Purpose.PRODUCT_LIKE, "full-detail-page")
    );

    // detail 계열만 예외적으로 Slot 자체가 바뀐다 — 선택된 리소스의 상세를 사이드 칼럼이 아니라 전체
    // 폭으로 보여주려면 목록(Block "list")이 차지하던 자리(page.main)를 대신 차지해야 한다. 이 변화가
    // 필요한 Variant만 여기 등록하고(나머지 계열은 등록 안 해 Slot 불변), REPLACES_OVERRIDE로 그
    // Block이 "list" 자리를 대신한다는 것을 함께 표시해 SlotCardinalityValidator가 page.main의
    // EXACTLY_ONE Cardinality를 위반됐다고 오판하지 않게 한다.
    private static final Map<String, String> SLOT_OVERRIDE = Map.of("full-detail-page", "page.main");
    private static final Map<String, String> REPLACES_OVERRIDE = Map.of("full-detail-page", "list");

    public static Map<String, List<Block>> compile(Map<String, List<Block>> pageBlocks, Purpose purpose) {
        if (purpose == null) {
            return pageBlocks;
        }

        Map<String, List<Block>> compiled = new LinkedHashMap<>();
        for (Map.Entry<String, List<Block>> entry : pageBlocks.entrySet()) {
            List<Block> blocks = entry.getValue().stream()
                    .map(block -> compileBlock(block, purpose))
                    .toList();
            compiled.put(entry.getKey(), blocks);
        }
        return compiled;
    }

    private static Block compileBlock(Block block, Purpose purpose) {
        Map<Purpose, String> variants = VARIANT_BY_PURPOSE.get(block.componentId());
        String preferredComponentId = variants != null ? variants.get(purpose) : null;
        if (preferredComponentId == null) {
            return block;
        }
        String slot = SLOT_OVERRIDE.getOrDefault(preferredComponentId, block.slot());
        String replaces = REPLACES_OVERRIDE.get(preferredComponentId);
        return new Block(block.instanceId(), preferredComponentId, slot, block.capabilityIds(), block.mode(), replaces);
    }

    private BlueprintCompiler() {
    }
}
