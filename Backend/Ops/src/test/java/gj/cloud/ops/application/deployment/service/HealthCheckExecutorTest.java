package gj.cloud.ops.application.deployment.service;

import com.jcraft.jsch.Session;
import gj.cloud.ops.application.deployment.dto.HealthCheck;
import gj.cloud.ops.global.ssh.CommandResult;
import gj.cloud.ops.global.ssh.SshCommandExecutor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthCheckExecutorTest {

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
}
