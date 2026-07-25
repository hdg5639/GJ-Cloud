package gj.cloud.ops.application.preview.dto;

import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.PageDraft;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

// /ops/preview/analyze가 돌려준 capabilities/pages(RULE_BASED 후보안)를 그대로 되돌려받아 AI에게
// 서비스에 맞는 페이지 재구성을 제안받는다. PreviewReviewRequest(코멘트만 반환)와 모양은 비슷하지만
// 이 요청의 결과는 실제로 pages를 대체한다는 점이 다르다.
public record PreviewPlanRequest(
        @Size(max = 2000) String serviceDescription,
        PreviewAnalyzeRequest.Purpose purpose,
        @NotEmpty List<Capability> capabilities,
        @NotEmpty List<PageDraft> pages
) {
}
