package gj.cloud.vm.application.collab.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CollaborationUpdateRequest(
        @Size(max = 50) String tag,
        @NotBlank @Size(max = 200) String title,
        @NotBlank String content
) {}
