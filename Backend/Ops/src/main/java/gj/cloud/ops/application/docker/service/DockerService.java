package gj.cloud.ops.application.docker.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.Session;
import gj.cloud.ops.application.docker.dto.ComposeStackInfo;
import gj.cloud.ops.application.docker.dto.ContainerInfo;
import gj.cloud.ops.application.docker.dto.ImageInfo;
import gj.cloud.ops.application.docker.dto.NetworkInfo;
import gj.cloud.ops.application.vmclient.VmServiceClient;
import gj.cloud.ops.application.vmclient.dto.VmContextResponse;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import gj.cloud.ops.global.ssh.CommandResult;
import gj.cloud.ops.global.ssh.SshCommandExecutor;
import gj.cloud.ops.global.ssh.VmSshSessionFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

// C절 Docker 관리 — 자체 UI(SSH 명령 + JSON 파싱)로 구현, Portainer는 별도 탭 링크만 제공(C.4).
// 조회(DOCKER_READ)와 제어(DOCKER_ADMIN) 권한을 분리해서 확인함 — Docker 제어는 VM root 권한과 동급이라
// Owner/Admin만 허용하고 Member는 조회만 가능해야 함(C.5).
@Slf4j
@Service
@RequiredArgsConstructor
public class DockerService {

    private static final String PERMISSION_DOCKER_READ = "DOCKER_READ";
    private static final String PERMISSION_DOCKER_ADMIN = "DOCKER_ADMIN";
    private static final long DOCKER_CMD_TIMEOUT_MS = 30_000;
    private static final long INSTALL_TIMEOUT_MS = 300_000;
    private static final long LOGS_TIMEOUT_MS = 20_000;
    private static final Set<String> SAFE_NETWORK_DRIVERS = Set.of("bridge", "host", "overlay", "macvlan", "none");
    // 컨테이너/이미지 ID, 컨테이너/네트워크 이름 등 요청 경로에서 들어오는 값은 셸 커맨드에 그대로 꽂히므로 반드시 검증
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[A-Za-z0-9._-]+$");

    private final VmServiceClient vmServiceClient;
    private final VmSshSessionFactory sshSessionFactory;
    private final SshCommandExecutor sshCommandExecutor;
    private final ObjectMapper objectMapper;

    public boolean isDockerInstalled(String bearerToken, String vmId) {
        return execute(bearerToken, vmId, PERMISSION_DOCKER_READ,
                session -> sshCommandExecutor.exec(session, "command -v docker", 10_000).isSuccess());
    }

    public void installDocker(String bearerToken, String vmId) {
        execute(bearerToken, vmId, PERMISSION_DOCKER_ADMIN, session -> {
            CommandResult result = sshCommandExecutor.exec(session, "curl -fsSL https://get.docker.com | sh", INSTALL_TIMEOUT_MS);
            if (!result.isSuccess()) {
                throw new OpsException(OpsErrorCode.DOCKER_INSTALL_FAILED);
            }
            return null;
        });
    }

    public List<ContainerInfo> listContainers(String bearerToken, String vmId) {
        return execute(bearerToken, vmId, PERMISSION_DOCKER_READ, session ->
                parseJsonLines(runDockerOrThrow(session, "docker ps -a --format '{{json .}}'"), ContainerInfo.class));
    }

    public List<ImageInfo> listImages(String bearerToken, String vmId) {
        return execute(bearerToken, vmId, PERMISSION_DOCKER_READ, session ->
                parseJsonLines(runDockerOrThrow(session, "docker images --format '{{json .}}'"), ImageInfo.class));
    }

    public List<NetworkInfo> listNetworks(String bearerToken, String vmId) {
        return execute(bearerToken, vmId, PERMISSION_DOCKER_READ, session ->
                parseJsonLines(runDockerOrThrow(session, "docker network ls --format '{{json .}}'"), NetworkInfo.class));
    }

    public List<ComposeStackInfo> listComposeStacks(String bearerToken, String vmId) {
        return execute(bearerToken, vmId, PERMISSION_DOCKER_READ, session -> {
            String output = runDockerOrThrow(session, "docker compose ls --format json").trim();
            if (output.isEmpty()) {
                return List.of();
            }
            try {
                return objectMapper.readValue(output, new TypeReference<List<ComposeStackInfo>>() {});
            } catch (JsonProcessingException e) {
                log.warn("docker compose ls 출력 파싱 실패: {}", e.getMessage());
                return List.<ComposeStackInfo>of();
            }
        });
    }

    public String getContainerLogs(String bearerToken, String vmId, String containerId, int tailLines) {
        String id = sanitize(containerId);
        int tail = Math.max(1, Math.min(tailLines, 2000));
        return execute(bearerToken, vmId, PERMISSION_DOCKER_READ, session ->
                sshCommandExecutor.exec(session, "docker logs --tail " + tail + " " + id + " 2>&1", LOGS_TIMEOUT_MS).stdout());
    }

    public void startContainer(String bearerToken, String vmId, String containerId) {
        runDockerAdmin(bearerToken, vmId, "docker start " + sanitize(containerId));
    }

    public void stopContainer(String bearerToken, String vmId, String containerId) {
        runDockerAdmin(bearerToken, vmId, "docker stop " + sanitize(containerId));
    }

    public void restartContainer(String bearerToken, String vmId, String containerId) {
        runDockerAdmin(bearerToken, vmId, "docker restart " + sanitize(containerId));
    }

    public void removeContainer(String bearerToken, String vmId, String containerId) {
        runDockerAdmin(bearerToken, vmId, "docker rm -f " + sanitize(containerId));
    }

    public void removeImage(String bearerToken, String vmId, String imageId) {
        runDockerAdmin(bearerToken, vmId, "docker rmi " + sanitize(imageId));
    }

    public void createNetwork(String bearerToken, String vmId, String name, String driver) {
        String safeName = sanitize(name);
        String safeDriver = driver != null ? driver : "bridge";
        if (!SAFE_NETWORK_DRIVERS.contains(safeDriver)) {
            throw new OpsException(OpsErrorCode.INVALID_DOCKER_IDENTIFIER);
        }
        runDockerAdmin(bearerToken, vmId, "docker network create --driver " + safeDriver + " " + safeName);
    }

    public void removeNetwork(String bearerToken, String vmId, String networkId) {
        runDockerAdmin(bearerToken, vmId, "docker network rm " + sanitize(networkId));
    }

    private void runDockerAdmin(String bearerToken, String vmId, String command) {
        execute(bearerToken, vmId, PERMISSION_DOCKER_ADMIN, session -> {
            runDockerOrThrow(session, command);
            return null;
        });
    }

    private String runDockerOrThrow(Session session, String command) {
        CommandResult result = sshCommandExecutor.exec(session, command, DOCKER_CMD_TIMEOUT_MS);
        if (!result.isSuccess()) {
            log.error("docker 명령 실패: command={}, stderr={}", command, result.stderr());
            throw new OpsException(OpsErrorCode.DOCKER_COMMAND_FAILED);
        }
        return result.stdout();
    }

    private <T> List<T> parseJsonLines(String output, Class<T> type) {
        if (output == null || output.isBlank()) {
            return List.of();
        }
        List<T> result = new ArrayList<>();
        for (String line : output.split("\\r?\\n")) {
            if (line.isBlank()) {
                continue;
            }
            try {
                result.add(objectMapper.readValue(line, type));
            } catch (JsonProcessingException e) {
                log.warn("docker 출력 파싱 실패, 해당 줄 스킵: {}", e.getMessage());
            }
        }
        return result;
    }

    private String sanitize(String identifier) {
        if (identifier == null || !SAFE_IDENTIFIER.matcher(identifier).matches()) {
            throw new OpsException(OpsErrorCode.INVALID_DOCKER_IDENTIFIER);
        }
        return identifier;
    }

    private <T> T execute(String bearerToken, String vmId, String requiredPermission, SessionOperation<T> operation) {
        VmContextResponse context = vmServiceClient.getContext(bearerToken, vmId);
        if (!context.hasPermission(requiredPermission)) {
            throw new OpsException(OpsErrorCode.FORBIDDEN);
        }
        if (context.internalIp() == null || !"RUNNING".equals(context.status())) {
            throw new OpsException(OpsErrorCode.VM_NOT_RUNNING);
        }

        Session session = sshSessionFactory.createSession(vmId, context.internalIp());
        try {
            return operation.apply(session);
        } finally {
            if (session.isConnected()) {
                session.disconnect();
            }
        }
    }

    @FunctionalInterface
    private interface SessionOperation<T> {
        T apply(Session session);
    }
}
