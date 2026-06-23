package gj.cloud.user.application.upgrade.dto;

import gj.cloud.user.domain.plan.enums.PlanType;

public record CreateUpgradeRequestRequest(
        PlanType targetPlanType
) {}
