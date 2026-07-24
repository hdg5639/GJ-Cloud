package gj.cloud.ops.application.github.dto;

import jakarta.validation.constraints.NotBlank;

public record GithubInstallationCompleteRequest(
        @NotBlank String code,
        @NotBlank String state
) {
}
