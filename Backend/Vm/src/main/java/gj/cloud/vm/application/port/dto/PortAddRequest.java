package gj.cloud.vm.application.port.dto;

import gj.cloud.vm.domain.port.enums.Protocol;
import gj.cloud.vm.domain.port.enums.Visibility;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PortAddRequest(
        @NotNull @Min(1) @Max(65535) Integer port,
        @NotNull Protocol protocol,
        @NotNull Visibility visibility,
        @NotBlank @Size(min = 1, max = 20) @Pattern(regexp = "^[a-z0-9]([a-z0-9-]*[a-z0-9])?$") String nickname,
        List<@Email String> initialEmails,
        @Size(min = 1, max = 30) @Pattern(regexp = "^[a-z0-9]([a-z0-9-]*[a-z0-9])?$") String customSubdomain
) {}
