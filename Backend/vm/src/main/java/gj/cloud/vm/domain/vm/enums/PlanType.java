package gj.cloud.vm.domain.vm.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PlanType {
    FREE(9000, 4, "5120", "6-13"),
    PRO(9100, 8, "12288", "14-37");

    private final int templateVmid;
    private final int cores;
    private final String memory;    // MB
    private final String affinity;  // CPU 코어 핀닝 범위
}
