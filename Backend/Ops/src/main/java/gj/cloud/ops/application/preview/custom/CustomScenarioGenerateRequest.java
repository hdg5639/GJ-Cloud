package gj.cloud.ops.application.preview.custom;

import gj.cloud.ops.application.preview.dto.PreviewAnalyzeRequest.Purpose;
import gj.cloud.ops.domain.preview.enums.CustomScenarioVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomScenarioGenerateRequest(
        @NotBlank @Size(max = 64) String serviceId,
        @NotBlank @Size(max = 2048) String apiDocsUrl,
        @Size(max = 100) String name,
        @Size(max = 1000) String description,
        @NotBlank @Size(max = 4000) String naturalLanguageSource,
        Purpose purpose,
        CustomScenarioVisibility visibility
) {
}
