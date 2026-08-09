package gj.cloud.ops.application.systemworker;

import gj.cloud.ops.application.managementkey.service.ManagementKeyService;
import gj.cloud.ops.application.systemworker.dto.SystemWorkerVmResponse;
import gj.cloud.ops.domain.systemworker.entity.SystemWorkerEntity;
import gj.cloud.ops.domain.systemworker.enums.SystemWorkerRole;
import gj.cloud.ops.domain.systemworker.repository.SystemWorkerRepository;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SystemWorkerServiceTest {
    @Mock SystemWorkerRepository repository;
    @Mock ManagementKeyService keyService;
    @Mock SystemWorkerVmClient vmClient;
    @Mock SystemWorkerRuntimeService runtime;
    @Mock TaskExecutor executor;

    private SystemWorkerService service;

    @BeforeEach
    void setUp() {
        service = new SystemWorkerService(repository, new SystemWorkerProperties(), keyService, vmClient, runtime, executor);
        lenient().when(repository.save(any(SystemWorkerEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void getMarksWorkerMissingImmediatelyWhenProxmoxVmWasDeleted() {
        SystemWorkerEntity worker = activeWorker();
        when(repository.findByRole(SystemWorkerRole.AUTO_PREVIEW)).thenReturn(Optional.of(worker));
        when(vmClient.status(300)).thenReturn(missingVm());

        var response = service.get();

        assertThat(response.status()).isEqualTo("MISSING");
        assertThat(response.internalIp()).isNull();
        assertThat(response.provisioningStage()).isEqualTo("MISSING");
        assertThat(response.lastError()).isEqualTo("Proxmox VM이 없습니다.");
    }

    @Test
    void createAllowsReprovisionWhenVmIsActuallyMissingEvenIfDatabaseStillSaysActive() {
        SystemWorkerEntity worker = activeWorker();
        when(repository.findByRole(SystemWorkerRole.AUTO_PREVIEW)).thenReturn(Optional.of(worker));
        when(vmClient.status(300)).thenReturn(missingVm());
        when(keyService.rotate(worker.getSshKeyRef())).thenReturn("ssh-ed25519 AAAATEST ops");

        TransactionSynchronizationManager.initSynchronization();
        try {
            var response = service.create();

            assertThat(response.status()).isEqualTo("PROVISIONING");
            assertThat(response.provisioningStage()).isEqualTo("REGISTERING");
            verify(keyService).rotate(worker.getSshKeyRef());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void createRejectsReprovisionWhenReservedVmidStillExistsEvenIfDatabaseSaysMissing() {
        SystemWorkerEntity worker = activeWorker().missing("pve");
        when(repository.findByRole(SystemWorkerRole.AUTO_PREVIEW)).thenReturn(Optional.of(worker));
        when(vmClient.status(300)).thenReturn(runningVm());

        assertThatThrownBy(service::create)
                .isInstanceOfSatisfying(OpsException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(OpsErrorCode.SYSTEM_WORKER_ALREADY_CONFIGURED));
        verifyNoInteractions(keyService);
    }

    private SystemWorkerEntity activeWorker() {
        return SystemWorkerEntity.provisioning("Auto Preview Worker", 300, 4, 5120, 80)
                .healthy("pve", "192.0.2.30");
    }

    private SystemWorkerVmResponse missingVm() {
        return new SystemWorkerVmResponse(false, 300, "pve", null, "MISSING", 4, 5120, 80);
    }

    private SystemWorkerVmResponse runningVm() {
        return new SystemWorkerVmResponse(true, 300, "pve", "192.0.2.30", "RUNNING", 4, 5120, 80);
    }
}
