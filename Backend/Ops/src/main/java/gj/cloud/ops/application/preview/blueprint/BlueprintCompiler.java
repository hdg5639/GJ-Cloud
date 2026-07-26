package gj.cloud.ops.application.preview.blueprint;

import gj.cloud.ops.application.preview.analysis.Block;
import gj.cloud.ops.application.preview.analysis.ComponentContract;
import gj.cloud.ops.application.preview.analysis.ComponentContracts;
import gj.cloud.ops.application.preview.dto.PreviewAnalyzeRequest.Purpose;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Direction Recovery Change Request §11 — PagePlan(어떤 Capability가 어느 페이지에 속하는지)과 별개로,
// 이미 정해진 Block 목록에서 실제로 어떤 Component Variant를 마운트할지 purpose 기준으로 고른다.
//
// Workflow Composition Phase 2 Change Request §13 WP-5 "Replace direct map substitution with
// structured candidate selection" — 예전엔 이 클래스 안에 Map<기본componentId, Map<Purpose,대체>>를
// 하드코딩해뒀다(Direction Recovery §10.2 "Retrieval order"가 예고했던 일반화 대상). 이제
// ComponentContract.family()/preferredPurposes()가 그 매핑을 데이터로 들고 있고, 여기서는 "같은
// family를 가진 후보를 모아 purpose가 선호하는 것을 고른다"는 절차만 수행한다 — §13의 8단계 선택
// 절차 중 3번(resolve required component families)과 4번(filter incompatible variants)에 해당.
// 2번(resolve layout)/5번(validate required API bindings)/6번(inject mandatory safety
// blocks)/7번(pin versions)은 LayoutBlueprint(§10)·버전 개념이 아직 없어 범위 밖 — 명시적으로 미룬다.
public final class BlueprintCompiler {

    // detail 계열만 예외적으로 Slot 자체가 바뀐다 — 선택된 리소스의 상세를 사이드 칼럼이 아니라 전체
    // 폭으로 보여주려면 목록(Block "list")이 차지하던 자리(page.main)를 대신 차지해야 한다. 이 변화가
    // 필요한 Variant만 여기 등록하고(나머지 계열은 등록 안 해 Slot 불변), REPLACES_OVERRIDE로 그
    // Block이 "list" 자리를 대신한다는 것을 함께 표시해 SlotCardinalityValidator가 page.main의
    // EXACTLY_ONE Cardinality를 위반됐다고 오판하지 않게 한다. family/preferredPurposes와 달리 이
    // 둘은 "어떤 Variant를 고를지"가 아니라 "고른 뒤 Slot 배치가 어떻게 달라지는지"라 별개 축이다.
    private static final Map<String, String> SLOT_OVERRIDE = Map.of("full-detail-page", "page.main");
    private static final Map<String, String> REPLACES_OVERRIDE = Map.of("full-detail-page", "list");

    // §13 "Example selection reason" 축소판 — matchedSurface/riskPolicy는 지금 데이터로 없어(Slot
    // Contract·위험 정책이 Registry에 아직 없음) componentId/capabilityIds/matchedPurpose만 담는다.
    public record SelectionReason(String instanceId, String componentId, List<String> matchedCapabilities,
                                   Purpose matchedPurpose) {
    }

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

    // §13 8단계 중 8번(generate selection reasons) — compile()과 별도 메서드로 둔 이유는 Block
    // record에 이유를 담을 필드가 없어서다(다른 소비자가 없는데 Block을 또 넓히고 싶지 않음, WP-1이
    // 세운 "소비자 생기기 전엔 최소만" 원칙과 동일). 지금은 어떤 화면도 이 결과를 안 씀 — 다음 소비자
    // (예: WP-7 검토 UI 확장)가 생기면 그때 연결한다.
    public static Map<String, List<SelectionReason>> explain(Map<String, List<Block>> pageBlocks, Purpose purpose) {
        Map<String, List<SelectionReason>> reasons = new LinkedHashMap<>();
        for (Map.Entry<String, List<Block>> entry : pageBlocks.entrySet()) {
            List<SelectionReason> pageReasons = entry.getValue().stream()
                    .map(block -> new SelectionReason(block.instanceId(), block.componentId(), block.capabilityIds(),
                            purpose))
                    .toList();
            reasons.put(entry.getKey(), pageReasons);
        }
        return reasons;
    }

    private static Block compileBlock(Block block, Purpose purpose) {
        String preferredComponentId = resolvePreferredVariant(block.componentId(), purpose);
        if (preferredComponentId == null || preferredComponentId.equals(block.componentId())) {
            return block;
        }
        String slot = SLOT_OVERRIDE.getOrDefault(preferredComponentId, block.slot());
        String replaces = REPLACES_OVERRIDE.get(preferredComponentId);
        return new Block(block.instanceId(), preferredComponentId, slot, block.capabilityIds(), block.mode(), replaces);
    }

    // family가 같은 Contract를 전부 모아 이 purpose를 preferredPurposes에 담은 것을 찾는다(§13
    // "Resolve required component families" + "Filter incompatible variants"). family가 없는
    // 컴포넌트(단독)나 이 purpose를 선호하는 대안이 없으면 null(그대로 둔다는 뜻).
    private static String resolvePreferredVariant(String componentId, Purpose purpose) {
        ComponentContract current = ComponentContracts.ALL.get(componentId);
        if (current == null || current.family() == null) {
            return null;
        }
        Optional<ComponentContract> preferred = ComponentContracts.ALL.values().stream()
                .filter(c -> current.family().equals(c.family()))
                .filter(c -> c.preferredPurposes().contains(purpose))
                .findFirst();
        return preferred.map(ComponentContract::componentId).orElse(null);
    }

    private BlueprintCompiler() {
    }
}
