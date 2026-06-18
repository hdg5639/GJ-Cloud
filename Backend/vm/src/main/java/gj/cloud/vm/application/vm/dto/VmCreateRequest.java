package gj.cloud.vm.application.vm.dto;

import gj.cloud.vm.domain.vm.enums.PlanType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record VmCreateRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull PlanType planType,
        @NotBlank String sshKeyId
) {}
