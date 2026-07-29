package gj.cloud.ops.application.preview.dto;

import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.PageDraft;
import gj.cloud.ops.application.preview.planning.model.PagePlan;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

// /parts/suggest — 현재 계획을 기본 Block으로 컴파일한 뒤, 스왑 가능한 Block마다 AI가 Blueprint 파츠를
// 추천한다. serviceDescription이 추천 근거이므로 함께 받는다. pagePlans가 있으면 그것을, 없으면 pages를
// 쓰는 규칙은 /blocks와 동일하다.
public record PreviewPartSuggestRequest(
        @Size(max = 12000) String serviceDescription,
        PreviewAnalyzeRequest.Purpose purpose,
        @NotEmpty List<Capability> capabilities,
        @NotEmpty List<PageDraft> pages,
        List<PagePlan> pagePlans
) {
}
