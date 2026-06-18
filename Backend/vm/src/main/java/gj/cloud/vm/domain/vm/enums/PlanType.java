package gj.cloud.vm.domain.vm.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Getter
@RequiredArgsConstructor
public enum PlanType {
    FREE(9000, 4, "5120", "6-13", List.of(
            "192.168.0.100", "192.168.0.101", "192.168.0.102", "192.168.0.103", "192.168.0.104"
    )),
    PRO(9100, 8, "12288", "14-37", List.of(
            "192.168.0.110", "192.168.0.111", "192.168.0.112"
    ));

    private final int templateVmid;
    private final int cores;
    private final String memory;    // MB
    private final String affinity;  // CPU 코어 핀닝 범위
    private final List<String> ipPool;
}
