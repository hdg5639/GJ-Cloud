package gj.cloud.user.application.admin.dto;

import gj.cloud.user.domain.plan.enums.PlanType;
import jakarta.validation.constraints.NotNull;

public record PlanUpdateRequest(
        @NotNull(message = "planType은 필수입니다")
        PlanType planType
) {}
