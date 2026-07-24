package gj.cloud.ops.application.filebrowser.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateDirectoryRequest(String parentPath, @NotBlank String name) {
}
