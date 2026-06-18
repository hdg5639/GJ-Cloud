package gj.cloud.user.application.sshkey.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SshKeyRegisterRequest(
        @Schema(description = "SSH 공개키 전체 문자열", example = "ssh-rsa AAAAB3NzaC1yc2E... user@host")
        @NotBlank String publicKey,

        @Schema(description = "키 식별 이름", example = "my-macbook")
        @NotBlank @Size(max = 100) String name
) {}
