package gj.cloud.ops.application.docker.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateNetworkRequest(@NotBlank String name, String driver) {
}
