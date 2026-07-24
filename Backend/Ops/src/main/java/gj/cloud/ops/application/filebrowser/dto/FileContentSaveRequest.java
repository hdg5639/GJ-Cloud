package gj.cloud.ops.application.filebrowser.dto;

import jakarta.validation.constraints.NotNull;

public record FileContentSaveRequest(@NotNull String content) {
}
