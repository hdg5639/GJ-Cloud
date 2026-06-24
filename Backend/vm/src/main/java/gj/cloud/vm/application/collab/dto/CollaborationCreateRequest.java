package gj.cloud.vm.application.collab.dto;

import gj.cloud.vm.domain.collab.enums.CollaborationType;
import gj.cloud.vm.domain.collab.enums.ScopeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CollaborationCreateRequest(
        @NotNull ScopeType scopeType,
        @NotNull UUID scopeId,
        @NotNull CollaborationType type,
        @Size(max = 50) String tag,
        @NotBlank @Size(max = 200) String title,
        @NotBlank String content
) {}
