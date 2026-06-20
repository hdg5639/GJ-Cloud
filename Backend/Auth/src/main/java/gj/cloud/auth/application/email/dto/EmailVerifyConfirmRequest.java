package gj.cloud.auth.application.email.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EmailVerifyConfirmRequest(
        @NotBlank @Email String email,
        @NotBlank String code
) {}
