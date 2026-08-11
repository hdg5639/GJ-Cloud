package gj.cloud.ops.application.deployment.service;

import gj.cloud.ops.application.vmclient.VmAutomationClient;
import gj.cloud.ops.domain.deployment.repository.ActiveDeploymentTargetVmRef;
import gj.cloud.ops.domain.deployment.repository.DeploymentTargetRepository;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DeploymentOrphanReconcilerTest {
    private final DeploymentTargetRepository targetRepository = mock(DeploymentTargetRepository.class);
    private final VmAutomationClient vmAutomationClient = mock(VmAutomationClient.class);
    private final DeploymentOrphanMutationService mutationService = mock(DeploymentOrphanMutationService.class);
    private final DeploymentOrphanReconciler reconciler = new DeploymentOrphanReconciler(
            targetRepository, vmAutomationClient, mutationService);

    @Test
    void cleansOnlyVmIdsAbsentFromBatchResponse() {
        String missingVmId = "00000000-0000-0000-0000-000000000001";
        String existingVmId = "00000000-0000-0000-0000-000000000002";
        var missing = target("target-missing", missingVmId);
        var existing = target("target-existing", existingVmId);
        when(targetRepository.findActiveTargetVmRefs()).thenReturn(List.of(missing, existing));
        when(vmAutomationClient.findExistingVmIds(Set.of(missingVmId, existingVmId)))
                .thenReturn(Set.of(existingVmId));
        when(mutationService.handleMissingVm("target-missing", "VM_NOT_FOUND"))
                .thenReturn(DeploymentOrphanMutationService.CleanupAction.QUARANTINED);

        var result = reconciler.reconcileNow();

        assertThat(result.scanned()).isEqualTo(2);
        assertThat(result.missing()).isEqualTo(1);
        assertThat(result.quarantined()).isEqualTo(1);
        assertThat(result.errors()).isZero();
        verify(mutationService).handleMissingVm("target-missing", "VM_NOT_FOUND");
        verify(mutationService, never()).handleMissingVm(eq("target-existing"), anyString());
    }

    @Test
    void leavesWholeBatchUntouchedWhenVmServiceIsUnavailable() {
        String firstVmId = "00000000-0000-0000-0000-000000000001";
        String secondVmId = "00000000-0000-0000-0000-000000000002";
        when(targetRepository.findActiveTargetVmRefs()).thenReturn(List.of(
                target("target-1", firstVmId), target("target-2", secondVmId)));
        when(vmAutomationClient.findExistingVmIds(Set.of(firstVmId, secondVmId)))
                .thenThrow(new OpsException(OpsErrorCode.VM_CONTEXT_FETCH_FAILED));

        var result = reconciler.reconcileNow();

        assertThat(result.scanned()).isEqualTo(2);
        assertThat(result.missing()).isZero();
        assertThat(result.errors()).isEqualTo(2);
        verifyNoInteractions(mutationService);
    }

    @Test
    void invalidLegacyVmIdIsAnErrorAndIsNeverDeleted() {
        when(targetRepository.findActiveTargetVmRefs())
                .thenReturn(List.of(target("target-invalid", "not-a-uuid")));

        var result = reconciler.reconcileNow();

        assertThat(result.scanned()).isEqualTo(1);
        assertThat(result.missing()).isZero();
        assertThat(result.errors()).isEqualTo(1);
        verifyNoInteractions(vmAutomationClient, mutationService);
    }

    private ActiveDeploymentTargetVmRef target(String id, String vmId) {
        return new ActiveDeploymentTargetVmRef(id, vmId);
    }
}
