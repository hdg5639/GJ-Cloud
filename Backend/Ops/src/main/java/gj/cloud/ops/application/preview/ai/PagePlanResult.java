package gj.cloud.ops.application.preview.ai;

import gj.cloud.ops.application.preview.analysis.PageDraft;

import java.util.List;

// aiSucceeded=false면 pages는 입력으로 받은 후보 목록 그대로다 — AiPagePlanner는 절대 깨진 페이지를
// 반환하지 않는다(검증 실패 시 안전하게 폴백).
public record PagePlanResult(
        List<PageDraft> pages,
        List<String> decisions,
        boolean aiSucceeded
) {
}
