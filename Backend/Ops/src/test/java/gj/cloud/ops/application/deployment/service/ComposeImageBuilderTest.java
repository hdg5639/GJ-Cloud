package gj.cloud.ops.application.deployment.service;

import com.jcraft.jsch.Session;
import gj.cloud.ops.application.deployment.dto.ResolvedCompose;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.ssh.CommandResult;
import gj.cloud.ops.global.ssh.SshCommandExecutor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ComposeImageBuilderTest {

    private final SshCommandExecutor ssh = mock(SshCommandExecutor.class);
    private final DeploymentEventPublisher events = mock(DeploymentEventPublisher.class);
    private final ComposeImageBuilder builder = new ComposeImageBuilder(ssh, events);
    private final Session session = mock(Session.class);

    @Test
    void passesBuildArgsToDockerBuild() {
        String compose = """
                services:
                  events-api:
                    build:
                      context: .
                      args:
                        MODULE: gamjabox-crud-api
                    expose: [8080]
                """;
        when(ssh.exec(eq(session), contains("docker build"), anyLong()))
                .thenReturn(new CommandResult(0, "built", ""));
        when(ssh.execOrThrow(eq(session), contains("docker image inspect"), anyLong()))
                .thenReturn(new CommandResult(0, "sha256:abc", ""));

        ResolvedCompose resolved = builder.buildAndResolve(session, "app1", "dep1", "/release", compose);

        ArgumentCaptor<String> command = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(ssh).exec(eq(session), command.capture(), anyLong());
        assertThat(command.getValue()).contains("--build-arg 'MODULE=gamjabox-crud-api'");
        assertThat(resolved.serviceImageRefs()).containsKey("events-api");
    }

    @Test
    void rejectsBuildArgWithShellInjection() {
        String compose = """
                services:
                  events-api:
                    build:
                      context: .
                      args:
                        MODULE: "a; rm -rf /"
                    expose: [8080]
                """;

        assertThatThrownBy(() -> builder.buildAndResolve(session, "app1", "dep1", "/release", compose))
                .isInstanceOf(OpsException.class);
    }
}
