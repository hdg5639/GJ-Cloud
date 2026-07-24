package gj.cloud.ops.application.deployment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.Session;
import gj.cloud.ops.application.deployment.dto.DeploymentRoutesRequest;
import gj.cloud.ops.application.deployment.git.GitReleaseManager;
import gj.cloud.ops.domain.deployment.entity.DeploymentEntity;
import gj.cloud.ops.domain.deployment.enums.DeploymentStatus;
import gj.cloud.ops.domain.deployment.enums.SourceType;
import gj.cloud.ops.domain.deployment.repository.DeploymentRepository;
import gj.cloud.ops.global.ssh.CommandResult;
import gj.cloud.ops.global.ssh.SshCommandExecutor;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RollbackServiceTest {

    @Test
    void restoresThePreviousDeploymentsRoutesForTheSameApp() {
        DeploymentRepository repository = mock(DeploymentRepository.class);
        DeploymentEventPublisher eventPublisher = mock(DeploymentEventPublisher.class);
        GitReleaseManager gitReleaseManager = mock(GitReleaseManager.class);
        SshCommandExecutor sshCommandExecutor = mock(SshCommandExecutor.class);
        Session session = mock(Session.class);
        RollbackService service = new RollbackService(
                repository,
                eventPublisher,
                gitReleaseManager,
                sshCommandExecutor,
                new ObjectMapper());

        LocalDateTime now = LocalDateTime.now();
        DeploymentEntity current = DeploymentEntity.builder()
                .id("current")
                .vmId("vm-1")
                .deploymentTargetId("app-1")
                .previousDeploymentId("previous")
                .status(DeploymentStatus.SWAPPING)
                .sourceType(SourceType.RAW_COMPOSE)
                .createdAt(now)
                .updatedAt(now)
                .build();
        DeploymentEntity previous = DeploymentEntity.builder()
                .id("previous")
                .vmId("vm-1")
                .deploymentTargetId("app-1")
                .status(DeploymentStatus.SUCCEEDED)
                .sourceType(SourceType.RAW_COMPOSE)
                .releaseDir("/releases/previous")
                .exposedRoutesJson("""
                        [{"serviceName":"web","port":8080,"protocol":"HTTP",
                          "visibility":"PUBLIC","nickname":"web","customSubdomain":null}]
                        """)
                .createdAt(now)
                .updatedAt(now)
                .build();
        when(repository.findById("current")).thenReturn(Optional.of(current));
        when(repository.findById("previous")).thenReturn(Optional.of(previous));
        when(repository.save(any(DeploymentEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(sshCommandExecutor.exec(any(Session.class), any(String.class), anyLong()))
                .thenReturn(new CommandResult(0, "", ""));
        AtomicReference<DeploymentRoutesRequest> synchronizedRoutes = new AtomicReference<>();

        service.rollback(session, "current", "app-1", synchronizedRoutes::set, "health check failed");

        assertThat(synchronizedRoutes.get().deploymentAppId()).isEqualTo("app-1");
        assertThat(synchronizedRoutes.get().deploymentId()).isEqualTo("previous");
        assertThat(synchronizedRoutes.get().routes())
                .singleElement()
                .satisfies(route -> {
                    assertThat(route.nickname()).isEqualTo("web");
                    assertThat(route.port()).isEqualTo(8080);
                });
    }
}
