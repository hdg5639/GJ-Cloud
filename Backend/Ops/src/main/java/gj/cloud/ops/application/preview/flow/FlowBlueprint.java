package gj.cloud.ops.application.preview.flow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Workflow Composition Phase 2 §6의 실행 가능한 순차 Workflow. 규칙 기반 생성기와 WP-4
// ADD_FLOW/ASSIGN_FLOW가 만들며, Portal과 배포 Runtime의 FlowExecutor가 동일한 계약으로 실행한다.
public record FlowBlueprint(
        String id,
        FlowTrigger trigger,
        List<FlowStep> steps
) {
    public FlowBlueprint {
        steps = steps == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(steps));
    }

    public record FlowTrigger(String pageId, String actionId) {
    }
}
