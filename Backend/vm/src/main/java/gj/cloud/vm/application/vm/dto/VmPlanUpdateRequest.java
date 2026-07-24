package gj.cloud.vm.application.vm.dto;

import gj.cloud.vm.domain.vm.enums.PlanType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record VmPlanUpdateRequest(
        @Schema(description = "변경할 플랜", allowableValues = {"FREE", "PRO"})
        @NotNull PlanType planType,

        @Schema(description = "변경할 디스크 크기 (GB). 현재보다 작을 수 없음.", example = "100")
        @NotNull @Min(1) Integer diskSizeGb
) {}
