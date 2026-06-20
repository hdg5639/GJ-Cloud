package gj.cloud.vm.application.port.dto;

import gj.cloud.vm.domain.port.enums.Protocol;
import gj.cloud.vm.domain.port.enums.Visibility;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record PortAddRequest(
        @NotNull @Min(1) @Max(65535) Integer port,
        @NotNull Protocol protocol,
        @NotNull Visibility visibility,
        List<@Email String> initialEmails
) {}
