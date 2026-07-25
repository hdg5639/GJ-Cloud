package gj.cloud.ops.application.preview.blueprint;

import gj.cloud.ops.application.preview.analysis.Block;
import gj.cloud.ops.application.preview.dto.PreviewAnalyzeRequest.Purpose;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Direction Recovery Change Request §11 — PagePlan(어떤 Capability가 어느 페이지에 속하는지)과 별개로,
// 이미 정해진 Block 목록에서 실제로 어떤 Component Variant를 마운트할지 purpose 기준으로 고른다.
// 지금은 list 계열(resource-table ↔ resource-card-grid) Variant가 하나뿐이라 명시적 매핑이면
// 충분하다 — Variant가 여러 계열로 늘어나면 그때 Component Contract에 family/preferredPurposes 같은
// 일반화된 검색 필드를 추가한다(§10.2 Retrieval order).
public final class BlueprintCompiler {

    private static final String LIST_DEFAULT_COMPONENT_ID = "resource-table";
    private static final Map<Purpose, String> LIST_VARIANT_BY_PURPOSE = Map.of(
            Purpose.PRODUCT_LIKE, "resource-card-grid"
    );

    public static Map<String, List<Block>> compile(Map<String, List<Block>> pageBlocks, Purpose purpose) {
        String preferredListComponentId = purpose != null ? LIST_VARIANT_BY_PURPOSE.get(purpose) : null;
        if (preferredListComponentId == null) {
            return pageBlocks;
        }

        Map<String, List<Block>> compiled = new LinkedHashMap<>();
        for (Map.Entry<String, List<Block>> entry : pageBlocks.entrySet()) {
            List<Block> blocks = entry.getValue().stream()
                    .map(block -> LIST_DEFAULT_COMPONENT_ID.equals(block.componentId())
                            ? new Block(block.instanceId(), preferredListComponentId, block.slot(),
                                    block.capabilityIds(), block.mode())
                            : block)
                    .toList();
            compiled.put(entry.getKey(), blocks);
        }
        return compiled;
    }

    private BlueprintCompiler() {
    }
}
