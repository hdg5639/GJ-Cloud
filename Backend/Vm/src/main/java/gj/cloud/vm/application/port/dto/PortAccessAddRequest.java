package gj.cloud.vm.application.port.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record PortAccessAddRequest(
        @NotBlank @Email String email
) {}
