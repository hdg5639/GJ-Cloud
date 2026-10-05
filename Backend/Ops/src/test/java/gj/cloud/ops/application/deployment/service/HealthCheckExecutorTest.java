package gj.cloud.ops.application.deployment.service;

import com.jcraft.jsch.Session;
import gj.cloud.ops.application.deployment.dto.HealthCheck;
import gj.cloud.ops.global.ssh.CommandResult;
import gj.cloud.ops.global.ssh.SshCommandExecutor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;

class HealthCheckExecutorTest {

    @Test
    void runningContainerWithClosedApplicationPortIsNotReady() {
        when(ssh.exec(eq(session), anyString(), anyLong()))
                .thenReturn(new CommandResult(0, "running 0", ""), new CommandResult(1, "", "Connection refused"));
        assertThat(executor.check(session, "app", new HealthCheck("api", "", null, 8080, true))).isFalse();
        verify(ssh).exec(eq(session), argThat(command -> command.contains("/dev/tcp/$1/$2")
                && command.contains("8080") && command.contains("for container_id")), anyLong());
    }

    private final SshCommandExecutor ssh = mock(SshCommandExecutor.class);
    private final Session session = mock(Session.class);
    private final HealthCheckExecutor executor = new HealthCheckExecutor(ssh);

    @Test
    void returnsHttpStatusForDetailedFailureLog() {
        HealthCheck healthCheck = new HealthCheck("api", "/actuator/health", 8080, null);
        when(ssh.exec(eq(session), contains("127.0.0.1:8080/actuator/health"), anyLong()))
                .thenReturn(new CommandResult(0, "503", ""));

        HealthCheckResult result = executor.checkDetailed(session, "app-1", healthCheck);

        assertThat(result.healthy()).isFalse();
        assertThat(result.httpStatus()).isEqualTo(503);
        assertThat(result.commandResult().exitStatus()).isZero();
    }

    @Test
    void retainsCurlFailureOutput() {
        HealthCheck healthCheck = new HealthCheck("api", "/health", 8080, null);
        when(ssh.exec(eq(session), contains("127.0.0.1:8080/health"), anyLong()))
                .thenReturn(new CommandResult(7, "000", "Failed to connect"));

        HealthCheckResult result = executor.checkDetailed(session, "app-1", healthCheck);

        assertThat(result.healthy()).isFalse();
        assertThat(result.httpStatus()).isZero();
        assertThat(result.commandResult().stderr()).contains("Failed to connect");
    }
 @Test void checksEveryReplicaAndRejectsExitedServers() {
  var check = new HealthCheck("api", "", null, null, true);
  for (String state : new String[]{"running 0\nrunning 0 unhealthy", "running 0\nexited 1", "exited 0", ""}) {
   when(ssh.exec(eq(session),anyString(),anyLong())).thenReturn(new CommandResult(0,state,""));
   assertThat(executor.check(session,"app",check)).as(state).isFalse();
  }
  when(ssh.exec(eq(session),anyString(),anyLong())).thenReturn(new CommandResult(0,"running 0 healthy\nrunning 0 ",""));
  assertThat(executor.check(session,"app",check)).isTrue();
  verify(ssh,atLeastOnce()).exec(eq(session),argThat(command -> command.contains("docker inspect") && !command.contains("head -n")),anyLong());
 }
 @Test void permitsCompletedMigrationOnlyWhenExplicitlySelected() {
  when(ssh.exec(eq(session),anyString(),anyLong())).thenReturn(new CommandResult(0,"exited 0",""));
  assertThat(executor.check(session,"app",new HealthCheck("migration","",null,null,true,true))).isTrue();
 }
 @Test void directContainerHttpCheckUsesHostCurlAndDockerLabels() {
  when(ssh.exec(eq(session),anyString(),anyLong())).thenReturn(new CommandResult(0,"200",""));
  assertThat(executor.check(session,"app",new HealthCheck("api","/health",null,8080))).isTrue();
  verify(ssh).exec(eq(session),argThat(command -> command.contains("com.docker.compose.service=api") && command.contains("http://$ip:8080/health") && !command.contains("compose exec")),anyLong());
 }
}
