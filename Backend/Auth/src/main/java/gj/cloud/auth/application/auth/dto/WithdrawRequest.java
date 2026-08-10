package gj.cloud.auth.application.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record WithdrawRequest(
        @NotBlank String password
) {}
