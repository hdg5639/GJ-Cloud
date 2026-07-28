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
        return select(pageBlocks, capabilities, purpose, Map.of());
    }

    // overrides: "pageId/instanceId" → 사용자(또는 AI)가 지정한 componentId. Phase C 사용자 오버라이드/
    // ASsIGN_PART의 토대. 지정값이 (a) 그 Block과 호환되는 파츠면 그걸로 치환, (b) 기본 컴포넌트 id면
    // 자동선택을 건너뛰고 기본 유지, 그 외/미지정이면 결정론 자동선택으로 폴백한다.
    public static Map<String, List<Block>> select(Map<String, List<Block>> pageBlocks,
                                                   List<Capability> capabilities, Purpose purpose,
                                                   Map<String, String> overrides) {
        if (pageBlocks == null || pageBlocks.isEmpty()) {
            return pageBlocks;
        }
        Map<String, String> safeOverrides = overrides == null ? Map.of() : overrides;
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
            String pageId = entry.getKey();
            List<Block> selected = entry.getValue().stream()
                    .map(block -> selectBlock(pageId, block, byId, purpose, safeOverrides))
                    .toList();
            result.put(pageId, selected);
        }
        return result;
    }

    private static Block selectBlock(String pageId, Block block, Map<String, Capability> byId, Purpose purpose,
                                     Map<String, String> overrides) {
        var kind = BlueprintPartRegistry.kindOfBaseComponent(block.componentId());
        if (kind.isEmpty()) {
            return block;
        }

        String override = overrides.get(pageId + "/" + block.instanceId());
        if (override != null && !override.isBlank()) {
            // 기본 컴포넌트 id를 지정 = 자동선택 끄고 기본 유지.
            if (override.equals(block.componentId())) {
                return block;
            }
            // 지정 파츠가 이 Block(kind/slot)과 호환되면 그걸로 치환. 아니면 무시하고 자동선택으로.
            return BlueprintPartRegistry.ALL.stream()
                    .filter(part -> part.componentId().equals(override))
                    .filter(part -> part.kind() == kind.get())
                    .filter(part -> part.acceptedSurfaces().contains(block.slot()))
                    .findFirst()
                    .map(part -> withComponent(block, part.componentId()))
                    .orElseGet(() -> autoSelect(block, byId, purpose, kind.get()));
        }
        return autoSelect(block, byId, purpose, kind.get());
    }

    private static Block autoSelect(Block block, Map<String, Capability> byId, Purpose purpose,
                                    BlueprintPartRegistry.PartKind kind) {
        Capability primary = primaryCapability(block, byId);
        if (primary == null) {
            return block;
        }
        BlueprintCategory category = CLASSIFIER.classify(primary);
        if (category == null) {
            return block;
        }
        return BlueprintPartRegistry.find(kind, category, block.slot(), purpose)
                .map(part -> withComponent(block, part.componentId()))
                .orElse(block);
    }

    private static Block withComponent(Block block, String componentId) {
        return new Block(block.instanceId(), componentId, block.slot(),
                block.capabilityIds(), block.mode(), block.replaces());
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
