package gj.cloud.ops.application.preview.ai;

import java.util.List;

// aiSucceeded=false면 operations는 항상 빈 목록이다 — AI 호출 자체가 실패한 경우.
public record PagePlanProposalResult(
        List<PagePlanOperationView> operations,
        boolean aiSucceeded
) {
}
