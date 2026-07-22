package gj.cloud.ops.application.docker.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.Session;
import gj.cloud.ops.application.docker.dto.ComposeStackInfo;
import gj.cloud.ops.application.docker.dto.ContainerInfo;
import gj.cloud.ops.application.docker.dto.DockerStatusResponse;
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
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
    // AUTHZ-001: 컨테이너 로그는 애플리케이션 시크릿이 그대로 노출될 수 있어 목록/상태 조회(DOCKER_READ)와 분리
    private static final String PERMISSION_DOCKER_LOG_READ = "DOCKER_LOG_READ";
    private static final long DOCKER_CMD_TIMEOUT_MS = 30_000;
    private static final long LOGS_TIMEOUT_MS = 20_000;
    private static final int STEP_RETRY_ATTEMPTS = 3;
    private static final long STEP_RETRY_DELAY_MS = 5_000;
    private static final Set<String> SAFE_NETWORK_DRIVERS = Set.of("bridge", "host", "overlay", "macvlan", "none");
    // 컨테이너/이미지 ID, 컨테이너/네트워크 이름 등 요청 경로에서 들어오는 값은 셸 커맨드에 그대로 꽂히므로 반드시 검증
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[A-Za-z0-9._-]+$");

    private final VmServiceClient vmServiceClient;
    private final VmSshSessionFactory sshSessionFactory;
    private final SshCommandExecutor sshCommandExecutor;
    private final ObjectMapper objectMapper;
    private final TaskExecutor deploymentTaskExecutor;

    // CICD-004: 설치는 수 분 걸릴 수 있는데 예전엔 HTTP 요청 스레드에서 그대로 블로킹했음 — 중간의 리버스
    // 프록시가 응답을 오래 기다리다 연결을 끊어버리면 브라우저엔 "Failed to fetch"만 뜨고, 서버는 그 사실을
    // 모른 채 계속 실행되다 결과를 이미 끊긴 연결에 쓰려다 버림(DeploymentExecutor와 동일한 문제/해법).
    // 요청 스레드는 권한/상태 확인 후 전용 워커에 위임하고 즉시 반환하며, 진행 상태는 이 메모리 맵으로
    // 추적해 프론트가 /status를 폴링하게 한다. Ops는 단일 인스턴스로만 운영하므로 인메모리로 충분.
    private final Set<String> installingVmIds = ConcurrentHashMap.newKeySet();
    private final Map<String, String> installStageByVmId = new ConcurrentHashMap<>();
    private final Map<String, String> installErrorByVmId = new ConcurrentHashMap<>();

    public DockerStatusResponse getStatus(String bearerToken, String vmId) {
        if (installingVmIds.contains(vmId)) {
            return new DockerStatusResponse(false, true, installStageByVmId.get(vmId), null);
        }
        boolean installed = isDockerInstalled(bearerToken, vmId);
        String lastError = installed ? null : installErrorByVmId.get(vmId);
        return new DockerStatusResponse(installed, false, null, lastError);
    }

    public boolean isDockerInstalled(String bearerToken, String vmId) {
        return execute(bearerToken, vmId, PERMISSION_DOCKER_READ,
                session -> sshCommandExecutor.exec(session, "command -v docker", 10_000).isSuccess());
    }

    public void requestInstall(String bearerToken, String vmId) {
        VmContextResponse context = vmServiceClient.getContext(bearerToken, vmId);
        if (!context.hasPermission(PERMISSION_DOCKER_ADMIN)) {
            throw new OpsException(OpsErrorCode.FORBIDDEN);
        }
        if (context.internalIp() == null || !"RUNNING".equals(context.status())) {
            throw new OpsException(OpsErrorCode.VM_NOT_RUNNING);
        }
        if (!installingVmIds.add(vmId)) {
            throw new OpsException(OpsErrorCode.DOCKER_INSTALL_IN_PROGRESS);
        }
        installErrorByVmId.remove(vmId);

        String internalIp = context.internalIp();
        deploymentTaskExecutor.execute(() -> runInstall(vmId, internalIp));
    }

    // CICD-005: 예전엔 전 단계를 &&로 묶어 하나의 SSH 명령으로 최대 10번 반복했음 — 막판(예: apt-get
    // install)에서 한 번만 실패해도 다음 재시도가 GPG 키 받기부터 전부 다시 하면서, 수동 설치(~1분)보다
    // 훨씬 오래(5분 이상) 걸리는 원인이었다. 이제 각 단계를 독립된 SSH 명령으로 나눠 단계별로만
    // 재시도하고(이미 끝난 단계는 다시 안 함), 현재 진행 중인 단계를 installStageByVmId에 남겨
    // /status 폴링으로 프론트에 보여준다.
    private void runInstall(String vmId, String internalIp) {
        Session session = null;
        try {
            session = sshSessionFactory.createSession(vmId, internalIp);

            // cloud-init은 동기화 목적일 뿐 실패해도(예: 마이너 모듈 오류) 설치 자체는 계속 진행 —
            // 기존 스크립트도 이 단계의 종료 코드는 확인하지 않았음(best-effort 대기).
            installStageByVmId.put(vmId, "사전 준비 확인 중...");
            sshCommandExecutor.exec(session, "cloud-init status --wait", 120_000);

            // DEP-004: 이전에는 get.docker.com에서 받은 설치 스크립트를 그대로 실행(curl | sh)했음 — 무결성
            // 검증 없이 원격 셸 스크립트를 실행하는 공급망 취약점. Docker 공식 apt 저장소(GPG 서명 검증)로
            // 대체 — apt가 패키지 서명을 검증하므로 변조된 패키지는 설치 자체가 거부된다.
            // 정확한 버전 고정은 Ubuntu 코드네임(lsb_release -cs)마다 패키지 문자열이 달라 VM 템플릿이
            // 바뀔 때마다 깨지기 쉬우므로, 대신 "공식 서명된 저장소에서만 설치"를 보안 경계로 삼는다.
            runStep(vmId, session, "Docker 공식 저장소 등록 중...",
                    "sudo install -m 0755 -d /etc/apt/keyrings"
                            + " && curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /tmp/docker.gpg"
                            + " && sudo gpg --batch --yes --dearmor -o /etc/apt/keyrings/docker.gpg /tmp/docker.gpg"
                            + " && rm -f /tmp/docker.gpg"
                            + " && sudo chmod a+r /etc/apt/keyrings/docker.gpg"
                            + " && CODENAME=$(. /etc/os-release && echo $VERSION_CODENAME)"
                            + " && echo \"deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg]"
                            + " https://download.docker.com/linux/ubuntu $CODENAME stable\""
                            + " | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null",
                    60_000);

            runStep(vmId, session, "패키지 목록 갱신 중...", "sudo apt-get update -y", 120_000);

            runStep(vmId, session, "Docker 설치 중... (패키지 다운로드가 오래 걸릴 수 있어요)",
                    "sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin",
                    240_000);

            runStep(vmId, session, "마무리 중...",
                    "sudo usermod -aG docker $(whoami) && command -v docker", 15_000);

            log.info("Docker 설치 완료(공식 apt 저장소, GPG 서명 검증): vmId={}", vmId);
        } catch (InstallStepFailedException e) {
            log.warn("Docker 설치 실패: vmId={}, step={}, exitStatus={}, stderr={}, stdout={}",
                    vmId, e.stepLabel, e.result.exitStatus(), trim(e.result.stderr()), trim(e.result.stdout()));
            installErrorByVmId.put(vmId, "Docker 설치에 실패했습니다 (" + e.stepLabel + ")");
        } catch (Exception e) {
            log.error("Docker 설치 처리 중 예외: vmId={}, error={}", vmId, e.getMessage());
            installErrorByVmId.put(vmId, "Docker 설치 중 오류가 발생했습니다.");
        } finally {
            installingVmIds.remove(vmId);
            installStageByVmId.remove(vmId);
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }

    // 단계 하나를 최대 STEP_RETRY_ATTEMPTS번만 재시도 — 이전 단계는 다시 실행하지 않으므로, 특정 단계가
    // 일시적 네트워크 문제 등으로 실패해도 그 단계만 이어서 재시도된다.
    private void runStep(String vmId, Session session, String stageLabel, String command, long timeoutMs) {
        installStageByVmId.put(vmId, stageLabel);
        CommandResult result = null;
        for (int attempt = 1; attempt <= STEP_RETRY_ATTEMPTS; attempt++) {
            result = sshCommandExecutor.exec(session, command, timeoutMs);
            if (result.isSuccess()) {
                return;
            }
            if (attempt < STEP_RETRY_ATTEMPTS) {
                sleepBeforeRetry();
            }
        }
        throw new InstallStepFailedException(stageLabel, result);
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(STEP_RETRY_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static class InstallStepFailedException extends RuntimeException {
        private final String stepLabel;
        private final CommandResult result;

        InstallStepFailedException(String stepLabel, CommandResult result) {
            super(stepLabel);
            this.stepLabel = stepLabel;
            this.result = result;
        }
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
        return execute(bearerToken, vmId, PERMISSION_DOCKER_LOG_READ, session ->
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

    // OBS-001: start/stop/restart/remove(container·image)/network 생성·삭제 전부 이 헬퍼를 거치므로
    // 여기 한 곳에서 구조화 로그 라인으로 기록 — VM root 권한과 동급인 조치들이라 추적성이 중요함.
    private void runDockerAdmin(String bearerToken, String vmId, String command) {
        execute(bearerToken, vmId, PERMISSION_DOCKER_ADMIN, session -> {
            runDockerOrThrow(session, command);
            return null;
        });
        log.info("AUDIT action=DOCKER_ADMIN_COMMAND targetType=VM targetId={} command={} result=SUCCESS", vmId, command);
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

    private String trim(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 1000 ? text.substring(0, 1000) : text;
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
