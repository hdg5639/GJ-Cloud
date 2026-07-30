package gj.cloud.ops.application.deployment.dto;

import jakarta.validation.constraints.NotBlank;

public record ComposeDetectionRequest(
        @NotBlank String repoUrl,
        @NotBlank String branch,
        String patToken,
        String context,
        Long githubInstallationId,
        Long githubRepositoryId
) {
}
