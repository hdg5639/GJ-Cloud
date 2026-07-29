package gj.cloud.ops.application.preview.dto;

import jakarta.validation.constraints.Size;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.PreviewMode;

import java.util.List;

// OpenAPI는 외부 URL 또는 사용자가 업로드한 원문 중 하나를 받는다. 서비스 문서 페이지와 자연어
// 시나리오는 의미 문맥으로만 사용하고, 실제 API 결합은 선택된 capability를 결정론적으로 검증한다.
public record PreviewAnalyzeRequest(
        @Size(max = 2048) String apiDocsUrl,
        @Size(max = 5_242_880) String apiDocsContent,
        @Size(max = 2048) String documentationPageUrl,
        @Size(max = 2000) String serviceDescription,
        @Size(max = 4000) String scenarioIntent,
        @Size(max = 300) List<@Size(max = 160) String> selectedCapabilityIds,
        Purpose purpose,
        PreviewMode previewMode
) {
    public PreviewAnalyzeRequest(String apiDocsUrl, String serviceDescription, Purpose purpose) {
        this(apiDocsUrl, null, null, serviceDescription, null, null, purpose, null);
    }

    public enum Purpose {
        API_TEST,
        PRODUCT_LIKE,
        ADMIN
    }
}
