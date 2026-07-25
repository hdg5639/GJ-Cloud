package gj.cloud.ops.application.preview.dto;

import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.PageDraft;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

// analyze(+review)가 돌려준 capabilities/pages를 그대로 되돌려받아 배포한다 — analyze/review와
// 동일하게 Ops는 분석 결과를 세션/DB로 들고 있지 않고 클라이언트가 확정한 최신 상태를 그대로 보낸다.
public record PreviewDeployRequest(
        @NotBlank @Size(max = 60) String targetName,
        @NotBlank String apiBaseUrl,
        @NotEmpty List<Capability> capabilities,
        @NotEmpty List<PageDraft> pages
) {
}
