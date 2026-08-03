package gj.cloud.vm.domain.vm.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Getter
@RequiredArgsConstructor
public enum PlanType {
    FREE(4, "5120", "6-13",
            List.of("192.168.0.100", "192.168.0.101", "192.168.0.102"),
            20, 50),
    PRO(8, "12288", "14-37",
            List.of("192.168.0.110", "192.168.0.111", "192.168.0.112"),
            20, 100);

    // FREE/PRO 공용 단일 템플릿. 코어 수·메모리·CPU 핀닝은 클론 직후 설정값으로 주입한다.
    public static final int TEMPLATE_VMID = 9026;

    private final int cores;
    private final String memory;    // MB
    private final String affinity;  // CPU 코어 핀닝 범위
    private final List<String> ipPool;
    private final int minDiskGb;
    private final int maxDiskGb;
}
