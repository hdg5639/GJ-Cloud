package gj.cloud.ops.application.sshreadiness.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SshReadinessRequest(
        @NotBlank
        @Pattern(regexp = "^(?:\\d{1,3}\\.){3}\\d{1,3}$")
        String internalIp,

        @NotBlank
        String expectedUserPublicKey,

        @NotBlank
        @Pattern(regexp = "^SHA256:[A-Za-z0-9+/]{43}$")
        String expectedUserKeyFingerprint
) {
}
