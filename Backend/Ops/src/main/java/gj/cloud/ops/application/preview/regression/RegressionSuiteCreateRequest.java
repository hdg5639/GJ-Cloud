package gj.cloud.ops.application.preview.regression;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.LinkedHashSet;

public record RegressionSuiteCreateRequest(
        @NotBlank @Size(max = 64) String serviceId,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 1000) String description,
        @NotBlank @Size(max = 2048) String apiDocsUrl,
        @NotBlank @Size(max = 2048) String apiBaseUrl,
        @NotEmpty @Size(max = 20) List<@NotBlank String> scenarioIds,
        @Size(max = 36) String deploymentTargetId,
        boolean runOnDeployment,
        boolean allowStateChangingOnDeployment
) {
    public RegressionSuiteCreateRequest {
        scenarioIds = scenarioIds == null
                ? List.of()
                : List.copyOf(new LinkedHashSet<>(scenarioIds));
    }
}
