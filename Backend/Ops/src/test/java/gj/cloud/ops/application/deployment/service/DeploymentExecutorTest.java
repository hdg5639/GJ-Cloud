package gj.cloud.ops.application.deployment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.Session;
import gj.cloud.ops.application.deployment.git.GitReleaseManager;
import gj.cloud.ops.application.deployment.validation.ComposeValidator;
import gj.cloud.ops.application.preview.analysis.AuthStrategy;
import gj.cloud.ops.application.preview.analysis.AutomationPolicy;
import gj.cloud.ops.application.preview.analysis.Block;
import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.CapabilityType;
import gj.cloud.ops.application.preview.analysis.PageDraft;
import gj.cloud.ops.application.preview.analysis.PageSkeletonType;
import gj.cloud.ops.application.preview.analysis.RiskLevel;
import gj.cloud.ops.application.preview.dto.PreviewBlueprintSnapshot;
import gj.cloud.ops.application.vmclient.VmDeploymentRoutesClient;
import gj.cloud.ops.application.vmclient.VmAutomationClient;
import gj.cloud.ops.application.vmclient.VmServiceClient;
import gj.cloud.ops.application.vmclient.dto.VmContextResponse;
import gj.cloud.ops.domain.deployment.entity.DeploymentEntity;
import gj.cloud.ops.domain.deployment.entity.DeploymentTargetEntity;
import gj.cloud.ops.domain.deployment.enums.DeploymentStatus;
import gj.cloud.ops.domain.deployment.enums.SourceType;
import gj.cloud.ops.domain.deployment.repository.DeploymentRepository;
import gj.cloud.ops.global.crypto.AesGcmCipher;
import gj.cloud.ops.global.ssh.CommandResult;
import gj.cloud.ops.global.ssh.SshCommandExecutor;
import gj.cloud.ops.global.ssh.VmSshSessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
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
    @Mock private VmAutomationClient vmAutomationClient;
    @Mock private VmSshSessionFactory sshSessionFactory;
    @Mock private SshCommandExecutor sshCommandExecutor;
    @Mock private GitReleaseManager gitReleaseManager;
    @Mock private ComposeValidator composeValidator;
    @Mock private ComposeImageBuilder composeImageBuilder;
    @Mock private HealthCheckExecutor healthCheckExecutor;
    @Mock private RollbackService rollbackService;
    @Mock private AesGcmCipher cipher;
    @Mock private DeploymentTargetService deploymentTargetService;
    @Mock private ObjectProvider<AutoDeploymentService> autoDeploymentServiceProvider;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    @Mock private TaskExecutor deploymentTaskExecutor;
    @Mock private Session session;

    @InjectMocks
    private DeploymentExecutor deploymentExecutor;

    @Test
    void teardownReleasesLockWhenStoppingStatusCannotBeSaved() {
        DeploymentEntity target = succeededDeployment();
        when(vmServiceClient.getContext(TOKEN, VM_ID)).thenReturn(runningDeployContext());
        when(deploymentRepository.findTopByVmIdAndDeploymentTargetIdIsNullAndStatusOrderByCreatedAtDesc(
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
        when(deploymentRepository.findTopByVmIdAndDeploymentTargetIdIsNullAndStatusOrderByCreatedAtDesc(
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

    @Test
    void teardownRemovesOnlyImagesBuiltForTheTargetDeployment() {
        String imageRefsJson = """
                {"api":{"imageTag":"gamjabox/vm-1/api:deployment-1","imageId":"sha256:api"},
                 "web":{"imageTag":"gamjabox/vm-1/web:deployment-1","imageId":"sha256:web"},
                 "other":{"imageTag":"gamjabox/vm-2/api:deployment-9","imageId":"sha256:other"}}
                """;
        DeploymentEntity target = succeededDeployment().toBuilder()
                .serviceImageRefsJson(imageRefsJson)
                .build();
        AtomicReference<DeploymentEntity> stored = new AtomicReference<>(target);

        when(vmServiceClient.getContext(TOKEN, VM_ID)).thenReturn(runningDeployContext());
        when(deploymentRepository.findTopByVmIdAndDeploymentTargetIdIsNullAndStatusOrderByCreatedAtDesc(
                VM_ID, DeploymentStatus.SUCCEEDED)).thenReturn(Optional.of(target));
        when(deploymentRepository.findById(DEPLOYMENT_ID))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(deploymentRepository.save(any(DeploymentEntity.class))).thenAnswer(invocation -> {
            DeploymentEntity saved = invocation.getArgument(0);
            stored.set(saved);
            return saved;
        });
        when(lockService.tryLock(VM_ID, DEPLOYMENT_ID)).thenReturn(true);
        when(sshSessionFactory.createSession(VM_ID, "10.0.0.10")).thenReturn(session);
        when(sshCommandExecutor.exec(any(Session.class), any(String.class), anyLong()))
                .thenReturn(new CommandResult(0, "", ""));
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(deploymentTaskExecutor).execute(any(Runnable.class));

        deploymentExecutor.teardown(TOKEN, VM_ID, target, List.of());

        verify(sshCommandExecutor).exec(
                session,
                "docker image rm 'gamjabox/vm-1/api:deployment-1' 'gamjabox/vm-1/web:deployment-1'",
                120_000);
        assertThat(stored.get().getStatus()).isEqualTo(DeploymentStatus.STOPPED);
        verify(lockService).unlock(VM_ID, DEPLOYMENT_ID);
    }

    // 배포 대상 완전 삭제는 컨테이너를 내리지만, 활성 배포(latestDeploymentId)의 DeploymentEntity.status를
    // 그대로 두면 배포 이력 목록에서 영원히 SUCCEEDED(=실행 중처럼 보임)로 남는다. runTeardown과 동일하게
    // STOPPED 처리 + 활성 배포 포인터 해제가 함께 일어나는지 검증한다.
    @Test
    void deleteTargetStopsTheActiveDeploymentHistoryEntry() {
        String targetId = "target-1";
        DeploymentTargetEntity target = DeploymentTargetEntity.builder()
                .id(targetId)
                .vmId(VM_ID)
                .ownerUserId("owner-1")
                .ownerEmail("owner@example.com")
                .name("my-app")
                .repositoryUrl("https://github.com/example/repo")
                .branch("main")
                .sourceType(SourceType.TEMPLATE_SPEC)
                .active(true)
                .latestDeploymentId(DEPLOYMENT_ID)
                .build();
        AtomicReference<DeploymentEntity> stored = new AtomicReference<>(succeededDeployment());

        when(vmServiceClient.getContext(TOKEN, VM_ID)).thenReturn(runningDeployContext());
        when(lockService.tryLock(targetId, "TARGET_DELETE")).thenReturn(true);
        when(sshSessionFactory.createSession(VM_ID, "10.0.0.10")).thenReturn(session);
        when(sshCommandExecutor.exec(any(Session.class), any(String.class), anyLong()))
                .thenReturn(new CommandResult(0, "", ""));
        when(deploymentRepository.findById(DEPLOYMENT_ID))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(deploymentRepository.save(any(DeploymentEntity.class))).thenAnswer(invocation -> {
            DeploymentEntity saved = invocation.getArgument(0);
            stored.set(saved);
            return saved;
        });

        deploymentExecutor.deleteTarget(TOKEN, VM_ID, target);

        assertThat(stored.get().getStatus()).isEqualTo(DeploymentStatus.STOPPED);
        verify(deploymentTargetService).clearActiveDeployment(targetId, DEPLOYMENT_ID);
        verify(deploymentTargetService).deactivate(targetId);
        verify(lockService).unlock(targetId, "TARGET_DELETE");
    }

    @Test
    void attachPreviewBlueprintPersistsAndRoundTripsThroughJson() {
        DeploymentEntity target = succeededDeployment();
        when(deploymentRepository.save(any(DeploymentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Capability login = new Capability("auth.login", "auth", CapabilityType.LOGIN, "login", "/auth/login", "POST",
                false, false, false, "HIGH", List.of(), List.of("email", "password"), "data.accessToken", null,
                RiskLevel.SAFE, AutomationPolicy.AUTO_SAFE, null, null);
        PageDraft page = new PageDraft("auth-login", "로그인", PageSkeletonType.AUTH_PAGE, List.of("auth.login"));
        Map<String, List<Block>> pageBlocks = Map.of("auth-login",
                List.of(new Block("login", "login-form", "page.content", List.of("auth.login"), null)));
        PreviewBlueprintSnapshot snapshot = new PreviewBlueprintSnapshot(
                "https://api.example.com", List.of(login), List.of(page), AuthStrategy.bearer(), pageBlocks);

        DeploymentEntity updated = deploymentExecutor.attachPreviewBlueprint(target, snapshot);

        verify(deploymentRepository).save(any(DeploymentEntity.class));
        assertThat(updated.getPreviewBlueprintJson()).isNotNull();
        assertThat(deploymentExecutor.getPreviewBlueprint(updated)).isEqualTo(snapshot);
    }

    @Test
    void getPreviewBlueprintReturnsNullForNonAutoPreviewDeployment() {
        DeploymentEntity target = succeededDeployment();

        assertThat(deploymentExecutor.getPreviewBlueprint(target)).isNull();
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
