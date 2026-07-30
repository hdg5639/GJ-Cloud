package gj.cloud.ops.application.deployment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ComposeReviewRequest(
        @NotBlank
        @Size(max = 1_048_576)
        String composeContent
) {
}
