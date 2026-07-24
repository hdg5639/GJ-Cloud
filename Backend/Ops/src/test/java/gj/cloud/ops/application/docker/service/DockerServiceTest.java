package gj.cloud.ops.application.docker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.Session;
import gj.cloud.ops.application.docker.dto.DockerStatusResponse;
import gj.cloud.ops.application.vmclient.VmServiceClient;
import gj.cloud.ops.application.vmclient.dto.VmContextResponse;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import gj.cloud.ops.global.ssh.CommandResult;
import gj.cloud.ops.global.ssh.SshCommandExecutor;
import gj.cloud.ops.global.ssh.VmSshSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DockerServiceTest {

    private static final String TOKEN = "Bearer test";
    private static final String VM_ID = "vm-1";
    private static final String INTERNAL_IP = "10.0.0.10";

    @Mock
    private VmServiceClient vmServiceClient;
    @Mock
    private VmSshSessionFactory sshSessionFactory;
    @Mock
    private SshCommandExecutor sshCommandExecutor;
    @Mock
    private Session session;

    private DockerService dockerService;

    @BeforeEach
    void setUp() {
        TaskExecutor directExecutor = Runnable::run;
        dockerService = new DockerService(
                vmServiceClient, sshSessionFactory, sshCommandExecutor, new ObjectMapper(), directExecutor);
        when(vmServiceClient.getContext(TOKEN, VM_ID)).thenReturn(runningAdminContext());
        when(sshSessionFactory.createSession(VM_ID, INTERNAL_IP)).thenReturn(session);
    }

    @Test
    void continuesInstallationWhenCloudInitWaitTimesOut() {
        when(sshCommandExecutor.exec(eq(session), anyString(), anyLong())).thenAnswer(invocation -> {
            String command = invocation.getArgument(1);
            if (command.equals("cloud-init status --wait")) {
                throw new OpsException(OpsErrorCode.SSH_COMMAND_TIMEOUT);
            }
            return success();
        });

        dockerService.requestInstall(TOKEN, VM_ID);
        DockerStatusResponse status = dockerService.getStatus(TOKEN, VM_ID);

        assertThat(status.installed()).isTrue();
        assertThat(status.installing()).isFalse();
        assertThat(status.lastError()).isNull();
        verify(sshCommandExecutor).exec(
                eq(session),
                argThat(command -> command.contains("DPkg::Lock::Timeout=300") && command.endsWith(" update")),
                eq(300_000L));
        verify(sshCommandExecutor).exec(
                eq(session),
                argThat(command -> command.contains("DEBIAN_FRONTEND=noninteractive")
                        && command.contains(" install -y docker-ce")),
                eq(900_000L));
    }

    @Test
    void reportsTheExactStageWhenDockerPackageInstallTimesOut() {
        when(sshCommandExecutor.exec(eq(session), anyString(), anyLong())).thenAnswer(invocation -> {
            String command = invocation.getArgument(1);
            if (command.contains(" install -y docker-ce")) {
                throw new OpsException(OpsErrorCode.SSH_COMMAND_TIMEOUT);
            }
            if (command.startsWith("command -v docker")) {
                return failure(1, "docker daemon unavailable");
            }
            return success();
        });

        dockerService.requestInstall(TOKEN, VM_ID);
        DockerStatusResponse status = dockerService.getStatus(TOKEN, VM_ID);

        assertThat(status.installed()).isFalse();
        assertThat(status.installing()).isFalse();
        assertThat(status.lastError())
                .contains("Docker 설치 중")
                .contains("시간 초과");
        verify(sshCommandExecutor, times(1)).exec(
                eq(session), argThat(command -> command.contains(" install -y docker-ce")), eq(900_000L));
    }

    @Test
    void installationStatusRequiresTheDockerDaemonToRespond() {
        when(sshCommandExecutor.exec(eq(session), anyString(), anyLong())).thenReturn(success());

        assertThat(dockerService.isDockerInstalled(TOKEN, VM_ID)).isTrue();

        verify(sshCommandExecutor).exec(
                session,
                "command -v docker >/dev/null && sudo -n docker info >/dev/null 2>&1",
                15_000);
    }

    private VmContextResponse runningAdminContext() {
        return new VmContextResponse(
                VM_ID, "owner-1", INTERNAL_IP, "RUNNING", "OWNER", List.of("DOCKER_READ", "DOCKER_ADMIN"));
    }

    private CommandResult success() {
        return new CommandResult(0, "", "");
    }

    private CommandResult failure(int exitStatus, String stderr) {
        return new CommandResult(exitStatus, "", stderr);
    }
}
