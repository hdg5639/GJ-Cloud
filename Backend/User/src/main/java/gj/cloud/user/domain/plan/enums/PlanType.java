package gj.cloud.user.domain.plan.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PlanType {
    FREE(4, 4, 3),
    PRO(8, 10, 3);

    private final int vCpu;
    private final int ramGb;
    private final int maxVmCount;
}
