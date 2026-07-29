package gj.cloud.ops.application.preview.dto;

import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.PageDraft;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

// /ops/preview/analyze가 돌려준 capabilities/pages를 그대로 되돌려받아 검수한다 — Ops 서버 쪽에
// 분석 결과를 세션/DB로 들고 있지 않고 매번 클라이언트가 최신 상태를 다시 보내는 방식
// (DeploymentController의 ai-spec/review가 DeploymentSpec을 그대로 되돌려받는 것과 동일한 패턴).
public record PreviewReviewRequest(
        @Size(max = 12000) String serviceDescription,
        @NotEmpty List<Capability> capabilities,
        @NotEmpty List<PageDraft> pages
) {
}
