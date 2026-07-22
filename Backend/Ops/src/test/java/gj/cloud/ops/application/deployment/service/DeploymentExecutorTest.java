package gj.cloud.ops.application.deployment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import gj.cloud.ops.application.deployment.git.GitReleaseManager;
import gj.cloud.ops.application.deployment.validation.ComposeValidator;
import gj.cloud.ops.application.vmclient.VmDeploymentRoutesClient;
import gj.cloud.ops.application.vmclient.VmServiceClient;
import gj.cloud.ops.application.vmclient.dto.VmContextResponse;
import gj.cloud.ops.domain.deployment.entity.DeploymentEntity;
import gj.cloud.ops.domain.deployment.enums.DeploymentStatus;
import gj.cloud.ops.domain.deployment.enums.SourceType;
import gj.cloud.ops.domain.deployment.repository.DeploymentRepository;
import gj.cloud.ops.global.crypto.AesGcmCipher;
import gj.cloud.ops.global.ssh.SshCommandExecutor;
import gj.cloud.ops.global.ssh.VmSshSessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeploymentExecutorTest {

    private static final String TOKEN = "Bearer test";
    private static final String VM_ID = "vm-1";
    private static final String DEPLOYMENT_ID = "deployment-1";

    @Mock private DeploymentRepository deploymentRepository;
    @Mock private DeploymentLockService lockService;
    @Mock private DeploymentEventPublisher eventPublisher;
    @Mock private VmServiceClient vmServiceClient;
    @Mock private VmDeploymentRoutesClient routesClient;
    @Mock private VmSshSessionFactory sshSessionFactory;
    @Mock private SshCommandExecutor sshCommandExecutor;
    @Mock private GitReleaseManager gitReleaseManager;
    @Mock private ComposeValidator composeValidator;
    @Mock private ComposeImageBuilder composeImageBuilder;
    @Mock private HealthCheckExecutor healthCheckExecutor;
    @Mock private RollbackService rollbackService;
    @Mock private AesGcmCipher cipher;
    @Mock private ObjectMapper objectMapper;
    @Mock private TaskExecutor deploymentTaskExecutor;

    @InjectMocks
    private DeploymentExecutor deploymentExecutor;

    @Test
    void teardownReleasesLockWhenStoppingStatusCannotBeSaved() {
        DeploymentEntity target = succeededDeployment();
        when(vmServiceClient.getContext(TOKEN, VM_ID)).thenReturn(runningDeployContext());
        when(deploymentRepository.findTopByVmIdAndStatusOrderByCreatedAtDesc(
                VM_ID, DeploymentStatus.SUCCEEDED)).thenReturn(Optional.of(target));
        when(lockService.tryLock(VM_ID, DEPLOYMENT_ID)).thenReturn(true);
        when(deploymentRepository.save(any(DeploymentEntity.class)))
                .thenThrow(new DataIntegrityViolationException("chk_deployment_status"));

        assertThatThrownBy(() -> deploymentExecutor.teardown(TOKEN, VM_ID, target, List.of()))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(lockService).unlock(VM_ID, DEPLOYMENT_ID);
        verifyNoInteractions(deploymentTaskExecutor);
    }

    @Test
    void teardownRestoresStatusAndReleasesLockWhenWorkerRejectsTheTask() {
        DeploymentEntity target = succeededDeployment();
        when(vmServiceClient.getContext(TOKEN, VM_ID)).thenReturn(runningDeployContext());
        when(deploymentRepository.findTopByVmIdAndStatusOrderByCreatedAtDesc(
                VM_ID, DeploymentStatus.SUCCEEDED)).thenReturn(Optional.of(target));
        when(lockService.tryLock(VM_ID, DEPLOYMENT_ID)).thenReturn(true);
        when(deploymentRepository.save(any(DeploymentEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new RuntimeException("worker queue full"))
                .when(deploymentTaskExecutor).execute(any(Runnable.class));

        assertThatThrownBy(() -> deploymentExecutor.teardown(TOKEN, VM_ID, target, List.of()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("worker queue full");

        ArgumentCaptor<DeploymentEntity> saved = ArgumentCaptor.forClass(DeploymentEntity.class);
        verify(deploymentRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues())
                .extracting(DeploymentEntity::getStatus)
                .containsExactly(DeploymentStatus.STOPPING, DeploymentStatus.SUCCEEDED);
        verify(lockService).unlock(VM_ID, DEPLOYMENT_ID);
    }

    private VmContextResponse runningDeployContext() {
        return new VmContextResponse(
                VM_ID, "owner-1", "10.0.0.10", "RUNNING", "OWNER", List.of("DEPLOY"));
    }

    private DeploymentEntity succeededDeployment() {
        LocalDateTime now = LocalDateTime.now();
        return DeploymentEntity.builder()
                .id(DEPLOYMENT_ID)
                .vmId(VM_ID)
                .status(DeploymentStatus.SUCCEEDED)
                .sourceType(SourceType.TEMPLATE_SPEC)
                .createdAt(now)
                .updatedAt(now)
                .deployedAt(now)
                .build();
    }
}
