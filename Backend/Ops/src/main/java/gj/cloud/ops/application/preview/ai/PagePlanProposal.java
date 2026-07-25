package gj.cloud.ops.application.preview.ai;

import java.util.List;

// OpenAI structured output 대상 타입 — AiPagePlanner가 이 형태로만 응답을 받는다.
public record PagePlanProposal(
        List<PagePlanOperation> operations
) {
}
