package gj.cloud.vm.application.vm.dto;

import gj.cloud.vm.domain.vm.enums.PlanType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VmCreateRequest(
        @NotBlank
        @Size(min = 1, max = 63)
        @Pattern(regexp = "^[a-zA-Z0-9]([a-zA-Z0-9\\-]*[a-zA-Z0-9])?$",
                message = "영문자, 숫자, 하이픈(-)만 사용 가능하며 하이픈으로 시작/끝날 수 없습니다")
        String name,
        @NotNull PlanType planType,
        @NotBlank String sshKeyId
) {}
