package gj.cloud.ops.application.preview.analysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// auto-preview-design/03-slot-contract.md §9 "Blueprint 조립 시" 검증의 축소판 — Block이 겨냥하는
// Slot을 그 페이지의 Skeleton이 실제로 제공하는지(SkeletonSlotContracts), Cardinality를 지키는지만
// 확인한다. Tag 기반 accept/forbid, 중첩 깊이, DESTRUCTIVE 이상일 때 modal.warning 필수 같은 조건부
// 규칙은 08-compatibility-rules.md의 몫이라 여기서 다루지 않는다.
public final class SlotCardinalityValidator {

    public static List<String> validate(PageSkeletonType skeleton, List<Block> blocks) {
        Map<String, Cardinality> providedSlots = SkeletonSlotContracts.ALL.get(skeleton);
        List<String> violations = new ArrayList<>();

        Map<String, Integer> countBySlot = new LinkedHashMap<>();
        for (Block block : blocks) {
            if (!providedSlots.containsKey(block.slot())) {
                violations.add("%s 스켈레톤은 %s Slot을 제공하지 않음(block=%s)"
                        .formatted(skeleton, block.slot(), block.instanceId()));
                continue;
            }
            countBySlot.merge(block.slot(), 1, Integer::sum);
        }

        providedSlots.forEach((slotId, cardinality) -> {
            int count = countBySlot.getOrDefault(slotId, 0);
            if (!cardinality.isSatisfiedBy(count)) {
                violations.add("%s Slot(%s)이 %s 조건을 어김(실제 %d개)"
                        .formatted(slotId, skeleton, cardinality, count));
            }
        });

        return violations;
    }

    private SlotCardinalityValidator() {
    }
}
