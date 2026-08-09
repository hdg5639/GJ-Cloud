package gj.cloud.vm.domain.vm.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PlanType {
    FREE(4, "4096", "6-13", 3, 20, 50),
    PRO(8, "10240", "14-37", 3, 20, 100);

    // FREE/PRO 공용 단일 템플릿. 코어 수·메모리·CPU 핀닝은 클론 직후 설정값으로 주입한다.
    public static final int TEMPLATE_VMID = 9026;

    private final int cores;
    private final String memory;    // MB
    private final String affinity;  // CPU 코어 핀닝 범위
    private final int maxVmCount;
    private final int minDiskGb;
    private final int maxDiskGb;
}
