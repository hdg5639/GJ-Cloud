package gj.cloud.ops.application.deployment.service;

import com.jcraft.jsch.Session;
import gj.cloud.ops.application.deployment.dto.ResolvedCompose;
import gj.cloud.ops.application.deployment.dto.DeploymentCommandLogPayload;
import gj.cloud.ops.domain.deployment.enums.DeploymentEventType;
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
import static org.mockito.Mockito.verify;
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
        verify(ssh).exec(eq(session), command.capture(), anyLong());
        assertThat(command.getValue()).contains("--build-arg 'MODULE=gamjabox-crud-api'");
        assertThat(resolved.serviceImageRefs()).containsKey("events-api");

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(events).publish(
                eq("dep1"),
                eq(DeploymentEventType.BUILD_LOG),
                contains("이미지 빌드 성공"),
                payload.capture());
        assertThat(payload.getValue()).isInstanceOfSatisfying(
                DeploymentCommandLogPayload.class,
                log -> {
                    assertThat(log.operation()).isEqualTo("docker-build");
                    assertThat(log.subject()).isEqualTo("events-api");
                    assertThat(log.exitStatus()).isZero();
                    assertThat(log.stdout()).isEqualTo("built");
                });
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

    @Test
    void preservesServiceAndDockerErrorInBuildFailure() {
        String compose = """
                services:
                  api:
                    build: .
                """;
        when(ssh.exec(eq(session), contains("docker build"), anyLong()))
                .thenReturn(new CommandResult(1, "", "Dockerfile parse error on line 7"));

        assertThatThrownBy(() -> builder.buildAndResolve(session, "app1", "dep1", "/release", compose))
                .isInstanceOf(OpsException.class)
                .hasMessageContaining("서비스 'api'")
                .hasMessageContaining("exit=1")
                .hasMessageContaining("Dockerfile parse error on line 7");
    }
}
