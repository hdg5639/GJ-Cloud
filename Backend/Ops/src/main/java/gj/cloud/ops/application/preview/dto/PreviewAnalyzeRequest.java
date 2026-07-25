package gj.cloud.ops.application.preview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// GamjaBox_2.0_Key_Features.md 2절 — 사용자가 입력하는 3가지. purpose는 Phase A의 결정론적 분석
// 로직에는 아직 영향을 주지 않고 향후(Phase E 이후) 생성 톤/우선순위 조정에 쓸 자리를 미리 받아둔 것.
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
