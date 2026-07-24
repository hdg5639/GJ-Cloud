package gj.cloud.auth.application.passwordreset.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetRequest(
        @NotBlank String resetToken,
        @NotBlank @Size(min = 8) String newPassword
) {}
