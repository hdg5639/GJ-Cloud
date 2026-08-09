package gj.cloud.ops.application.systemworker;

import com.jcraft.jsch.Session;
import gj.cloud.ops.domain.systemworker.entity.SystemWorkerEntity;
import gj.cloud.ops.global.ssh.CommandResult;
import gj.cloud.ops.global.ssh.SshCommandExecutor;
import gj.cloud.ops.global.ssh.VmSshSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemWorkerRuntimeServiceTest {
    private VmSshSessionFactory sessionFactory;
    private SshCommandExecutor commands;
    private Session session;
    private SystemWorkerRuntimeService service;
    private SystemWorkerEntity worker;

    @BeforeEach
    void setUp() {
        sessionFactory = mock(VmSshSessionFactory.class);
        commands = mock(SshCommandExecutor.class);
        session = mock(Session.class);
        service = new SystemWorkerRuntimeService(sessionFactory, commands);
        worker = SystemWorkerEntity.provisioning("Auto Preview Worker", 300, 4, 5120, 80)
                .healthy("pve", "10.0.0.30");
    }

    @Test
    void repairGrantsDockerAndPreviewDirectoryAccessToSshUser() {
        when(sessionFactory.createSession(worker.getSshKeyRef(), worker.getInternalIp())).thenReturn(session);

        service.repair(worker);

        ArgumentCaptor<String> command = ArgumentCaptor.forClass(String.class);
        verify(commands).execOrThrow(eq(session), command.capture(), anyLong());
        assertThat(command.getValue())
                .contains("usermod -aG docker \"$WORKER_USER\"")
                .contains("-o \"$WORKER_USER\" -g \"$WORKER_GROUP\" /opt/gamjabox/previews");
        verify(session).disconnect();
    }

    @Test
    void healthCheckRequiresDockerGroupAndWritablePreviewDirectory() {
        when(sessionFactory.tryCreateSession(worker.getSshKeyRef(), worker.getInternalIp()))
                .thenReturn(Optional.of(session));
        when(commands.exec(eq(session), org.mockito.ArgumentMatchers.anyString(), anyLong()))
                .thenReturn(new CommandResult(0, "", ""));

        assertThat(service.healthy(worker)).isTrue();

        ArgumentCaptor<String> command = ArgumentCaptor.forClass(String.class);
        verify(commands).exec(eq(session), command.capture(), anyLong());
        assertThat(command.getValue())
                .contains("grep -Fx docker")
                .contains("test -w /opt/gamjabox/previews")
                .contains("docker network inspect gamjabox-preview");
        verify(session).disconnect();
    }
}
