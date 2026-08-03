package gj.cloud.vm.application.port.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SshAccessAddRequest(
        @NotBlank @Email String email
) {}
