package gj.cloud.user.application.sshkey.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SshKeyRegisterRequest(
        @NotBlank String publicKey,
        @NotBlank @Size(max = 100) String name
) {}
