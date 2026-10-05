package gj.cloud.ops.application.preview.build;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.Session;
import gj.cloud.ops.application.vmclient.VmServiceClient;
import gj.cloud.ops.application.vmclient.dto.VmContextResponse;
import gj.cloud.ops.domain.deployment.repository.DeploymentTargetRepository;
import gj.cloud.ops.global.ssh.CommandResult;
import gj.cloud.ops.global.ssh.SshCommandExecutor;
import gj.cloud.ops.global.ssh.VmSshSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PreviewVmPortAllocatorTest {
    @Test
    @SuppressWarnings("unchecked")
    void skipsRegisteredListenersDockerAndQueuedTargetsAndReleasesResources() {
        var redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenAnswer(call -> {
            when(values.get(call.getArgument(0))).thenReturn(call.getArgument(1));
            return true;
        });
        var vm = mock(VmServiceClient.class);
        when(vm.getContext("token", "vm")).thenReturn(new VmContextResponse("vm", "owner", "example.test", "RUNNING", "OWNER", List.of("DEPLOY")));
        when(vm.getOccupiedPorts("token", "vm")).thenReturn(List.of(20000));
        var factory = mock(VmSshSessionFactory.class);
        var session = mock(Session.class);
        when(factory.createSession("vm", "example.test")).thenReturn(session);
        var executor = mock(SshCommandExecutor.class);
        when(executor.exec(eq(session), contains("ss -H"), eq(15_000L))).thenReturn(new CommandResult(0, "20001\n", ""));
        when(executor.exec(eq(session), contains("docker ps"), eq(15_000L))).thenReturn(new CommandResult(0, "20002\n", ""));
        var targets = mock(DeploymentTargetRepository.class);
        when(targets.findReservedRoutesByVmId("vm")).thenReturn(List.of("[{\"port\":20003}]"));
        var allocator = new PreviewVmPortAllocator(redis, vm, factory, executor, targets, new ObjectMapper());
        int allocated = allocator.withAvailablePort("token", "vm", port -> port);
        assertThat(allocated).isEqualTo(20004);
        verify(session).disconnect();
        verify(redis).execute(any(org.springframework.data.redis.core.script.RedisScript.class), eq(List.of("preview:vm-port-allocation:vm")), anyString());
        // A failed remote inspection must not create a target or assume every port is free.
        when(executor.exec(eq(session), contains("ss -H"), eq(15_000L))).thenReturn(new CommandResult(1, "", "unavailable"));
        assertThatThrownBy(() -> allocator.withAvailablePort("token", "vm", port -> { throw new AssertionError("must not persist"); })).isInstanceOf(gj.cloud.ops.global.exception.OpsException.class);
        verify(session, times(2)).disconnect();
    }

    @Test
    @SuppressWarnings("unchecked")
    void contendedLockDoesNotInspectVmOrPersistTarget() {
        var redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);
        var vm = mock(VmServiceClient.class);
        var factory = mock(VmSshSessionFactory.class);
        var executor = mock(SshCommandExecutor.class);
        var targets = mock(DeploymentTargetRepository.class);
        var allocator = new PreviewVmPortAllocator(redis, vm, factory, executor, targets, new ObjectMapper());
        assertThatThrownBy(() -> allocator.withAvailablePort("token", "vm", port -> {
            throw new AssertionError("must not persist without owning the lock");
        })).isInstanceOf(gj.cloud.ops.global.exception.OpsException.class);
        verifyNoInteractions(vm, factory, executor, targets);
    }

    @Test
    void usesDedicatedRange() {
        assertThat(PreviewVmPortAllocator.firstAvailable(Set.of(80, 8080, 20000))).isEqualTo(20001);
    }
}
