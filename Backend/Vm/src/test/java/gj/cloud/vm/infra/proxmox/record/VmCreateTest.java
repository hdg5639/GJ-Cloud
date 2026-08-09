package gj.cloud.vm.infra.proxmox.record;

import gj.cloud.vm.domain.vm.enums.PlanType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VmCreateTest {

    @Test
    void configuresCloudInitNetworkWithDhcp() {
        VmCreate config = VmCreate.from(
                PlanType.FREE,
                1000,
                "test-vm",
                List.of("ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAITest user@example.com"),
                "vmbr0",
                "local-lvm"
        );

        assertEquals("ip=dhcp", config.getIpconfig0());
        assertEquals("1.1.1.1 8.8.8.8", config.getNameserver());
    }

    @Test
    void keepsPlanCapacityIndependentFromNetworkAllocation() {
        assertEquals(3, PlanType.FREE.getMaxVmCount());
        assertEquals(3, PlanType.PRO.getMaxVmCount());
    }

    @Test
    void configuresPlanMemoryInMegabytes() {
        assertEquals("4096", PlanType.FREE.getMemory());
        assertEquals("10240", PlanType.PRO.getMemory());
    }
}
