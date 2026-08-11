package gj.cloud.vm.application.systemworker;

import gj.cloud.vm.application.systemworker.dto.SystemWorkerProvisionRequest;
import gj.cloud.vm.global.exception.VmException;
import gj.cloud.vm.infra.proxmox.client.ProxmoxClient;
import gj.cloud.vm.infra.proxmox.config.ProxmoxProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SystemWorkerProvisioningServiceTest {
    @Mock ProxmoxClient proxmoxClient;
    private SystemWorkerProvisioningService service;
    private SystemWorkerProperties properties;

    @BeforeEach
    void setUp() {
        properties = new SystemWorkerProperties();
        ProxmoxProperties proxmox = new ProxmoxProperties();
        proxmox.setNode("pve");
        service = new SystemWorkerProvisioningService(proxmoxClient, proxmox, properties);
        lenient().when(proxmoxClient.ensurePoolExists(properties.getPool())).thenReturn(Mono.empty());
        lenient().when(proxmoxClient.configureVm(eq(300), eq(4), eq(5120), anyList())).thenReturn(Mono.empty());
        lenient().when(proxmoxClient.resizeDisk(300, 80)).thenReturn(Mono.empty());
        lenient().when(proxmoxClient.startVm(300)).thenReturn(Mono.empty());
        lenient().when(proxmoxClient.waitForIpAssignment(300)).thenReturn(Mono.just("192.0.2.10"));
        lenient().when(proxmoxClient.findVmIpOnce(300)).thenReturn(Mono.just(Optional.of("192.0.2.10")));
        lenient().when(proxmoxClient.getSystemVmInfo(300))
                .thenReturn(Mono.just(new ProxmoxClient.SystemVmInfo(properties.getName(), 4, 5120, 80, "running")));
    }

    @Test
    void rejectsAnySpecificationThatDiffersFromServerContract() {
        SystemWorkerProvisionRequest request = request(301);

        assertThatThrownBy(() -> service.provision(request))
                .isInstanceOf(VmException.class);
        verifyNoInteractions(proxmoxClient);
    }

    @Test
    void doesNotDeleteReservedVmidWhenCloneNeverCompleted() {
        when(proxmoxClient.getProxmoxVmids()).thenReturn(Mono.just(Set.of()));
        when(proxmoxClient.cloneVm(300, 9026, properties.getName(), properties.getPool()))
                .thenReturn(Mono.error(new IllegalStateException("clone failed")));

        assertThatThrownBy(() -> service.provision(request(300)).block())
                .isInstanceOf(IllegalStateException.class);

        verify(proxmoxClient, never()).deleteVm(anyInt());
    }

    @Test
    void cleansUpOnlyAfterCloneTaskCompleted() {
        when(proxmoxClient.getProxmoxVmids()).thenReturn(Mono.just(Set.of()));
        when(proxmoxClient.cloneVm(300, 9026, properties.getName(), properties.getPool())).thenReturn(Mono.just("task"));
        when(proxmoxClient.waitForTaskCompletion("task")).thenReturn(Mono.empty());
        when(proxmoxClient.configureVm(eq(300), eq(4), eq(5120), anyList()))
                .thenReturn(Mono.error(new IllegalStateException("config failed")));
        when(proxmoxClient.deleteVm(300)).thenReturn(Mono.empty());

        assertThatThrownBy(() -> service.provision(request(300)).block())
                .isInstanceOf(IllegalStateException.class);

        verify(proxmoxClient).deleteVm(300);
    }

    @Test
    void statusUsesSingleIpProbeWithoutProvisioningWait() {
        when(proxmoxClient.getProxmoxVmids()).thenReturn(Mono.just(Set.of(300)));

        var response = service.status(300).block();

        org.assertj.core.api.Assertions.assertThat(response.internalIp()).isEqualTo("192.0.2.10");
        verify(proxmoxClient).findVmIpOnce(300);
        verify(proxmoxClient, never()).waitForIpAssignment(300);
    }

    private SystemWorkerProvisionRequest request(int vmId) {
        return new SystemWorkerProvisionRequest("AUTO_PREVIEW", vmId, 4, 5120, 80, 9026,
                "ssh-ed25519 AAAATEST ops");
    }
}
