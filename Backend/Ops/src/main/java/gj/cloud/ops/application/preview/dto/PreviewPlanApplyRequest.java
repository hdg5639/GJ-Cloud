package gj.cloud.ops.application.preview.dto;

import gj.cloud.ops.application.preview.ai.PagePlanOperation;
import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.PageDraft;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

// Plan Review UI(Increment 5 2부) — /plan/propose가 돌려준 PagePlanOperationView 중 사용자가 체크한
// 것만 골라(id/valid/validationError는 벗겨내고 원본 필드만) 다시 보낸다. AI를 다시 부르지 않고
// PagePlanValidator.apply만 태우는 순수 결정론적 요청이라 serviceDescription/purpose는 필요 없다.
public record PreviewPlanApplyRequest(
        @NotEmpty List<Capability> capabilities,
        @NotEmpty List<PageDraft> pages,
        @NotNull List<PagePlanOperation> operations
) {
}
