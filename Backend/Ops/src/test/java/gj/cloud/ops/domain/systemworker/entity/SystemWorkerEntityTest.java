package gj.cloud.ops.domain.systemworker.entity;

import gj.cloud.ops.domain.systemworker.enums.SystemWorkerStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SystemWorkerEntityTest {
    @Test
    void missingWorkerCanBeReprovisionedWithoutReplacingRegistryIdentity() {
        SystemWorkerEntity worker = SystemWorkerEntity.provisioning("Auto Preview Worker", 300, 4, 5120, 80)
                .observed(SystemWorkerStatus.MISSING, "pve", null, "missing");

        SystemWorkerEntity retried = worker.reprovisioning();

        assertThat(retried.getId()).isEqualTo(worker.getId());
        assertThat(retried.getSshKeyRef()).isEqualTo(worker.getSshKeyRef());
        assertThat(retried.getStatus()).isEqualTo(SystemWorkerStatus.PROVISIONING);
        assertThat(retried.getLastError()).isNull();
    }
}
