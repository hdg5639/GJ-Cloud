package gj.cloud.ops.application.preview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// GamjaBox_2.0_Key_Features.md 2절 — 사용자가 입력하는 3가지. purpose는 RuleBasedPagePlanGenerator가
// 페이지 구성(대시보드 포함 여부 등)에 실제로 반영한다(Direction Recovery Change Request Increment 2).
public record PreviewAnalyzeRequest(
        @NotBlank String apiDocsUrl,
        @Size(max = 2000) String serviceDescription,
        Purpose purpose
) {
    public enum Purpose {
        API_TEST,
        PRODUCT_LIKE,
        ADMIN
    }
}
