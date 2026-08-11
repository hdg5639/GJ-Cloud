package gj.cloud.ops.application.preview.managed;

import gj.cloud.ops.application.deployment.service.DeploymentExecutor;
import gj.cloud.ops.application.deployment.service.DeploymentTargetService;
import gj.cloud.ops.application.systemworker.SystemWorkerService;
import gj.cloud.ops.application.systemworker.SystemWorkerVmClient;
import gj.cloud.ops.application.userclient.UserPlanClient;
import gj.cloud.ops.domain.deployment.entity.DeploymentEntity;
import gj.cloud.ops.domain.deployment.enums.DeploymentStatus;
import gj.cloud.ops.domain.deployment.enums.SourceType;
import gj.cloud.ops.domain.deployment.repository.DeploymentRepository;
import gj.cloud.ops.domain.preview.entity.ManagedPreviewDeploymentEntity;
import gj.cloud.ops.domain.preview.repository.ManagedPreviewDeploymentRepository;
import gj.cloud.ops.global.ssh.SshCommandExecutor;
import gj.cloud.ops.global.ssh.VmSshSessionFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManagedPreviewServiceTest {

    @Test
    void listRefreshesAllDeploymentStatusesWithOneBatchQuery() {
        ManagedPreviewDeploymentRepository previewRepository = mock(ManagedPreviewDeploymentRepository.class);
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        ManagedPreviewService service = new ManagedPreviewService(
                previewRepository,
                deploymentRepository,
                mock(SystemWorkerService.class),
                mock(SystemWorkerVmClient.class),
                mock(UserPlanClient.class),
                new ManagedPreviewProperties(),
                mock(DeploymentTargetService.class),
                mock(DeploymentExecutor.class),
                mock(VmSshSessionFactory.class),
                mock(SshCommandExecutor.class));
        ManagedPreviewDeploymentEntity first = ManagedPreviewDeploymentEntity
                .allocate("user-1", "worker-1", 20001, 6).queued("target-1", "deployment-1");
        ManagedPreviewDeploymentEntity second = ManagedPreviewDeploymentEntity
                .allocate("user-1", "worker-1", 20002, 6).queued("target-2", "deployment-2");
        DeploymentEntity succeeded = deployment("deployment-1", DeploymentStatus.SUCCEEDED);
        DeploymentEntity building = deployment("deployment-2", DeploymentStatus.BUILDING);
        when(previewRepository.findAllByUserIdOrderByCreatedAtDesc("user-1"))
                .thenReturn(List.of(first, second));
        when(deploymentRepository.findAllById(List.of("deployment-1", "deployment-2")))
                .thenReturn(List.of(succeeded, building));
        when(previewRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.list("user-1");

        assertThat(result).extracting(response -> response.status()).containsExactly("RUNNING", "BUILDING");
        verify(deploymentRepository).findAllById(List.of("deployment-1", "deployment-2"));
        verify(deploymentRepository, never()).findById(any());
    }

    private DeploymentEntity deployment(String id, DeploymentStatus status) {
        return DeploymentEntity.builder()
                .id(id)
                .vmId("worker-1")
                .status(status)
                .sourceType(SourceType.RAW_COMPOSE)
                .build();
    }
}
