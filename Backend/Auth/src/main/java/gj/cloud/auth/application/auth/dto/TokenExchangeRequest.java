package gj.cloud.auth.application.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record TokenExchangeRequest(
        @NotBlank String targetService
) {}
