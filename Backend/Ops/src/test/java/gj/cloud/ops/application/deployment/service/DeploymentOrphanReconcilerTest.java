package gj.cloud.ops.application.deployment.service;

import gj.cloud.ops.application.vmclient.VmAutomationClient;
import gj.cloud.ops.domain.deployment.entity.DeploymentTargetEntity;
import gj.cloud.ops.domain.deployment.repository.DeploymentTargetRepository;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DeploymentOrphanReconcilerTest {
    private final DeploymentTargetRepository targetRepository = mock(DeploymentTargetRepository.class);
    private final VmAutomationClient vmAutomationClient = mock(VmAutomationClient.class);
    private final DeploymentOrphanMutationService mutationService = mock(DeploymentOrphanMutationService.class);
    private final DeploymentOrphanReconciler reconciler = new DeploymentOrphanReconciler(
            targetRepository, vmAutomationClient, mutationService);

    @Test
    void cleansOnlyConfirmedNotFoundAndLeavesTransientFailuresUntouched() {
        DeploymentTargetEntity missing = target("target-missing", "vm-missing");
        DeploymentTargetEntity unavailable = target("target-unavailable", "vm-unavailable");
        when(targetRepository.findAllByActiveTrueOrderByCreatedAtAsc()).thenReturn(List.of(missing, unavailable));
        when(vmAutomationClient.getContext("vm-missing", "user-1", "owner@example.com"))
                .thenThrow(new OpsException(OpsErrorCode.VM_NOT_FOUND));
        when(vmAutomationClient.getContext("vm-unavailable", "user-1", "owner@example.com"))
                .thenThrow(new OpsException(OpsErrorCode.VM_CONTEXT_FETCH_FAILED));
        when(mutationService.handleMissingVm("target-missing", "VM_NOT_FOUND"))
                .thenReturn(DeploymentOrphanMutationService.CleanupAction.QUARANTINED);

        var result = reconciler.reconcileNow();

        assertThat(result.scanned()).isEqualTo(2);
        assertThat(result.missing()).isEqualTo(1);
        assertThat(result.quarantined()).isEqualTo(1);
        assertThat(result.errors()).isEqualTo(1);
        verify(mutationService).handleMissingVm("target-missing", "VM_NOT_FOUND");
        verify(mutationService, never()).handleMissingVm(eq("target-unavailable"), anyString());
    }

    private DeploymentTargetEntity target(String id, String vmId) {
        return DeploymentTargetEntity.builder()
                .id(id)
                .vmId(vmId)
                .ownerUserId("user-1")
                .ownerEmail("owner@example.com")
                .active(true)
                .build();
    }
}
