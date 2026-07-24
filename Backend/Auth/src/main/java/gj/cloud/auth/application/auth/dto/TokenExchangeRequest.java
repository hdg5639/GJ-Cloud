package gj.cloud.auth.application.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record TokenExchangeRequest(
        @Schema(description = "교환 대상 서비스", allowableValues = {"user-service", "vm-service"})
        @NotBlank String targetService
) {}
