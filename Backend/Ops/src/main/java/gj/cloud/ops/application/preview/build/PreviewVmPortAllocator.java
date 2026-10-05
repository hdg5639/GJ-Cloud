package gj.cloud.ops.application.preview.build;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.Session;
import gj.cloud.ops.application.vmclient.VmServiceClient;
import gj.cloud.ops.domain.deployment.repository.DeploymentTargetRepository;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import gj.cloud.ops.global.ssh.VmSshSessionFactory;
import gj.cloud.ops.global.ssh.SshCommandExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntFunction;

/** Allocation and target persistence share a VM lock; queued targets reserve their ports too. */
@Component
@RequiredArgsConstructor
public class PreviewVmPortAllocator {
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end", Long.class);
    private final StringRedisTemplate redis;
    private final VmServiceClient vmClient;
    private final VmSshSessionFactory sshFactory;
    private final SshCommandExecutor commands;
    private final DeploymentTargetRepository targets;
    private final ObjectMapper mapper;

    public <T> T withAvailablePort(String token, String vmId, IntFunction<T> persistTarget) {
        String key = "preview:vm-port-allocation:" + vmId;
        String owner = UUID.randomUUID().toString();
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, owner, Duration.ofMinutes(2)))) {
            throw new OpsException(OpsErrorCode.DEPLOYMENT_IN_PROGRESS);
        }
        try {
            var context = vmClient.getContext(token, vmId);
            if (!context.hasPermission("DEPLOY")) throw new OpsException(OpsErrorCode.FORBIDDEN);
            if (!"RUNNING".equals(context.status()) || context.internalIp() == null) {
                throw new OpsException(OpsErrorCode.VM_NOT_RUNNING);
            }
            Set<Integer> occupied = new HashSet<>(vmClient.getOccupiedPorts(token, vmId));
            for (String json : targets.findReservedRoutesByVmId(vmId)) {
                try {
                    var routes = mapper.readTree(json);
                    if (!routes.isArray()) throw new IllegalArgumentException("Invalid reserved routes");
                    for (var route : routes) {
                        if (!route.path("port").canConvertToInt() || route.path("port").asInt() < 1) {
                            throw new IllegalArgumentException("Invalid reserved port");
                        }
                        occupied.add(route.path("port").asInt());
                    }
                } catch (Exception e) {
                    throw new OpsException(OpsErrorCode.INVALID_PREVIEW_BLUEPRINT);
                }
            }
            Session session = sshFactory.createSession(vmId, context.internalIp());
            try {
                collectPorts(session, "bash -o pipefail -c \"ss -H -ltn | awk '{n=split(\\$4,a,\\\":\\\"); print a[n]}'\"", occupied);
                collectPorts(session, "bash -o pipefail -c \"docker ps -q | xargs -r docker inspect --format '{{range .NetworkSettings.Ports}}{{range .}}{{println .HostPort}}{{end}}{{end}}'\"", occupied);
            } finally {
                session.disconnect();
            }
            if (!owner.equals(redis.opsForValue().get(key))) throw new OpsException(OpsErrorCode.DEPLOYMENT_IN_PROGRESS);
            return persistTarget.apply(firstAvailable(occupied));
        } finally {
            redis.execute(RELEASE, java.util.List.of(key), owner);
        }
    }

    private void collectPorts(Session session, String command, Set<Integer> occupied) {
        var result = commands.exec(session, command, 15_000);
        if (!result.isSuccess()) throw new OpsException(OpsErrorCode.SSH_COMMAND_FAILED);
        for (String value : result.stdout().split("\\s+")) {
            if (value.isBlank()) continue;
            try { occupied.add(Integer.parseInt(value)); }
            catch (NumberFormatException e) { throw new OpsException(OpsErrorCode.SSH_COMMAND_FAILED); }
        }
    }

    static int firstAvailable(Set<Integer> occupied) {
        for (int port = 20000; port <= 29999; port++) if (!occupied.contains(port)) return port;
        throw new OpsException(OpsErrorCode.DEPLOYMENT_IN_PROGRESS, "프리뷰에 할당할 빈 포트가 없습니다.");
    }
}
