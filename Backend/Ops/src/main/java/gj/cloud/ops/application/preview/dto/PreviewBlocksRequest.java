package gj.cloud.ops.application.preview.dto;

import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.PageDraft;
import gj.cloud.ops.application.preview.planning.model.PagePlan;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Map;

// pagePlans가 있으면 독립 RESOURCE_DETAIL 등 풍부한 계획을 보존한 채 Block을 계산한다. pages는
// 구버전 클라이언트 및 로컬 capability 편집 호환용 fallback이다.
public record PreviewBlocksRequest(
        @NotEmpty List<Capability> capabilities,
        @NotEmpty List<PageDraft> pages,
        List<PagePlan> pagePlans,
        PreviewAnalyzeRequest.Purpose purpose,
        // Phase C 사용자 오버라이드 — "pageId/blockInstanceId" → 강제 componentId(파츠 또는 기본
        // 컴포넌트). 없으면(null) 전부 결정론 자동선택. 마법사가 파츠를 바꿔 끼울 때 채운다.
        Map<String, String> partOverrides
) {
}
