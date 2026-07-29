package gj.cloud.ops.application.preview.custom;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CustomScenarioImportRequest(
        @NotBlank @Size(max = 64) String serviceId,
        @NotBlank @Size(max = 2048) String apiDocsUrl,
        @NotNull @Valid CustomScenarioExport scenario
) {
}
