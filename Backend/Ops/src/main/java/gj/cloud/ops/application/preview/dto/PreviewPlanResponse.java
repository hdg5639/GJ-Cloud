package gj.cloud.ops.application.preview.dto;

import gj.cloud.ops.application.preview.analysis.GenerationMode;
import gj.cloud.ops.application.preview.analysis.PageDraft;

import java.util.List;

// generationMode는 AI 제안이 실제로 검증·적용됐으면 SERVICE_AWARE, 실패해 후보 그대로 돌아왔으면
// RULE_BASED다 — FALLBACK_CRUD를 SERVICE_AWARE인 것처럼 보여주면 안 된다는 원칙(§17)과 동일하게,
// 여기서도 실패를 성공인 것처럼 포장하지 않는다.
public record PreviewPlanResponse(
        List<PageDraft> pages,
        List<String> decisions,
        GenerationMode generationMode
) {
}
