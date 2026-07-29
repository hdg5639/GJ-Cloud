package gj.cloud.ops.application.preview.custom;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomScenarioRevalidateRequest(
        @NotBlank @Size(max = 2048) String apiDocsUrl
) {
}
