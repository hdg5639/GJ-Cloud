package gj.cloud.vm.application.vm.dto;

import gj.cloud.vm.domain.vm.enums.PlanType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VmCreateRequest(
        @Schema(description = "VM 이름 (영문·숫자·하이픈, 하이픈으로 시작/끝 불가)", example = "my-vm")
        @NotBlank
        @Size(min = 1, max = 63)
        @Pattern(regexp = "^[a-zA-Z0-9]([a-zA-Z0-9\\-]*[a-zA-Z0-9])?$",
                message = "영문자, 숫자, 하이픈(-)만 사용 가능하며 하이픈으로 시작/끝날 수 없습니다")
        String name,

        @Schema(description = "플랜 (FREE: 4코어·4GB / PRO: 8코어·10GB)", allowableValues = {"FREE", "PRO"})
        @NotNull PlanType planType,

        @Schema(description = "디스크 크기 (GB). FREE: 20~50, PRO: 50~500", example = "20")
        @NotNull @Min(1) Integer diskSizeGb,

        @Schema(description = "등록된 SSH 공개키 ID")
        @NotBlank String sshKeyId
) {}
