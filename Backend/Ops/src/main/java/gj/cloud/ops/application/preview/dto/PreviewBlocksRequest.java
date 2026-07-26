package gj.cloud.ops.application.preview.dto;

import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.PageDraft;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

// Direction Recovery Change Request §13.1 — 라이브 프리뷰가 capability/페이지 편집(analyze/plan 응답
// 뿐 아니라 accessTokenPath 지정·수동 로그인 등록처럼 서버를 다시 안 거치는 로컬 편집 포함) 이후
// 매번 이 엔드포인트를 호출해 Block을 다시 계산받는다 — 프론트가 조립 규칙을 직접 판단하지 않는다.
public record PreviewBlocksRequest(
        @NotEmpty List<Capability> capabilities,
        @NotEmpty List<PageDraft> pages,
        PreviewAnalyzeRequest.Purpose purpose
) {
}
