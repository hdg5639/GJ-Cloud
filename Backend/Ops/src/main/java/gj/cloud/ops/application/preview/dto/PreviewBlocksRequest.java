package gj.cloud.ops.application.preview.dto;

import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.PageDraft;
import gj.cloud.ops.application.preview.planning.model.PagePlan;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

// pagePlans가 있으면 독립 RESOURCE_DETAIL 등 풍부한 계획을 보존한 채 Block을 계산한다. pages는
// 구버전 클라이언트 및 로컬 capability 편집 호환용 fallback이다.
public record PreviewBlocksRequest(
        @NotEmpty List<Capability> capabilities,
        @NotEmpty List<PageDraft> pages,
        List<PagePlan> pagePlans,
        PreviewAnalyzeRequest.Purpose purpose
) {
}
