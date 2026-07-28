package gj.cloud.ops.application.preview.blueprint;

import gj.cloud.ops.application.preview.analysis.Block;
import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.ResourceCategoryClassifier;
import gj.cloud.ops.application.preview.dto.PreviewAnalyzeRequest.Purpose;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// BlueprintCompiler.compile 직후 실행되는 결정론 선택 단계. 이미 purpose로 Variant가 정해진 Block의
// componentId를, 그 Block이 다루는 리소스의 카테고리에 맞는 Blueprint 파츠로 갈아끼운다.
// 근거(알려진 카테고리 + kind/slot/purpose 호환 파츠)가 있을 때만 치환하고, 없으면 기본 컴포넌트를
// 그대로 둔다 — CapabilityExtractor/분류기와 같은 "추측하지 않는다" 원칙.
//
// 주의: 리턴 Block의 slot/mode/replaces/capabilityIds는 보존한다. 파츠는 기본 컴포넌트와 같은 slot에서
// 같은 capability를 소비하는 드롭인 대체이므로 SlotCardinality/replaces 계산에 영향을 주지 않는다.
public final class BlueprintPartSelector {

    private static final ResourceCategoryClassifier CLASSIFIER = ResourceCategoryClassifier.getInstance();

    public static Map<String, List<Block>> select(Map<String, List<Block>> pageBlocks,
                                                   List<Capability> capabilities, Purpose purpose) {
        if (pageBlocks == null || pageBlocks.isEmpty()) {
            return pageBlocks;
        }
        Map<String, Capability> byId = new LinkedHashMap<>();
        if (capabilities != null) {
            for (Capability capability : capabilities) {
                if (capability != null && capability.id() != null) {
                    byId.putIfAbsent(capability.id(), capability);
                }
            }
        }

        Map<String, List<Block>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<Block>> entry : pageBlocks.entrySet()) {
            List<Block> selected = entry.getValue().stream()
                    .map(block -> selectBlock(block, byId, purpose))
                    .toList();
            result.put(entry.getKey(), selected);
        }
        return result;
    }

    private static Block selectBlock(Block block, Map<String, Capability> byId, Purpose purpose) {
        var kind = BlueprintPartRegistry.kindOfBaseComponent(block.componentId());
        if (kind.isEmpty()) {
            return block;
        }
        Capability primary = primaryCapability(block, byId);
        if (primary == null) {
            return block;
        }
        BlueprintCategory category = CLASSIFIER.classify(primary);
        if (category == null) {
            return block;
        }
        return BlueprintPartRegistry.find(kind.get(), category, block.slot(), purpose)
                .map(part -> new Block(block.instanceId(), part.componentId(), block.slot(),
                        block.capabilityIds(), block.mode(), block.replaces()))
                .orElse(block);
    }

    // Block이 대표하는 리소스를 정하는 capability — 첫 capabilityId 기준(목록/상세/대시보드 모두 첫 항목이
    // 주 리소스다).
    private static Capability primaryCapability(Block block, Map<String, Capability> byId) {
        return block.capabilityIds().stream()
                .filter(Objects::nonNull)
                .map(byId::get)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private BlueprintPartSelector() {
    }
}
