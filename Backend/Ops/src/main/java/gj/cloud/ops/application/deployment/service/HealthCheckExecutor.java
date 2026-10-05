package gj.cloud.ops.application.deployment.service;

import gj.cloud.ops.application.deployment.dto.HealthCheck;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import gj.cloud.ops.global.ssh.CommandResult;
import gj.cloud.ops.global.ssh.SshCommandExecutor;
import com.jcraft.jsch.Session;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

// D.7 헬스체크 기준 — 라우트 등록(8단계) 이전이라 외부 도메인 확인 불가, VM 내부에서만 확인.
// hostPort가 있으면 VM 호스트에서 직접 curl(127.0.0.1). containerPort만 있으면 서비스명 DNS는
// compose 네트워크 내부에서만 유효하므로 Docker 라벨로 컨테이너 IP를 찾고 VM의 curl로 실행한다.
@Component
@RequiredArgsConstructor
public class HealthCheckExecutor {

    private static final long CURL_TIMEOUT_MS = 15_000;
    // serviceName/path 모두 ComposeArtifact를 통해 들어오는 사용자 입력이라, 셸 커맨드에 꽂기 전에 반드시 검증함
    private static final Pattern SAFE_SERVICE_NAME = Pattern.compile("^[A-Za-z0-9_.-]+$");
    private static final Pattern SAFE_PATH = Pattern.compile("^/[A-Za-z0-9_./-]*$");

    private final SshCommandExecutor sshCommandExecutor;

    public boolean check(Session session, String appId, HealthCheck healthCheck) {
        return checkDetailed(session, appId, healthCheck).healthy();
    }

    HealthCheckResult checkDetailed(Session session, String appId, HealthCheck healthCheck) {
        sanitizeServiceName(appId);
        if (Boolean.TRUE.equals(healthCheck.readinessOnly())) return checkRuntime(session, appId, healthCheck);
        String path = sanitizePath(healthCheck.path());
        String command;
        if (healthCheck.hostPort() != null) {
            command = "curl -s -o /dev/null -w '%{http_code}' --max-time 10 'http://127.0.0.1:"
                    + requirePort(healthCheck.hostPort()) + path + "'";
        } else if (healthCheck.containerPort() != null) {
            String serviceName = sanitizeServiceName(healthCheck.serviceName());
            command = containerIdCommand(appId, serviceName)
                    + " ip=$(docker inspect --format '{{range .NetworkSettings.Networks}}{{.IPAddress}} {{end}}' \"$container_id\" | awk '{print $1}');"
                    + " case \"$ip\" in ''|*[!0-9.]*) exit 1;; esac;"
                    + " curl -s -o /dev/null -w '%{http_code}' --max-time 10 \"http://$ip:"
                    + requirePort(healthCheck.containerPort()) + path + "\"";
        } else {
            return new HealthCheckResult(false, null, new CommandResult(-1, "", "포트가 지정되지 않음"));
        }

        CommandResult result = sshCommandExecutor.exec(session, command, CURL_TIMEOUT_MS);
        Integer httpStatus = parseHttpStatus(result.stdout());
        if (!result.isSuccess()) {
            return new HealthCheckResult(false, httpStatus, result);
        }
        return new HealthCheckResult(
                httpStatus != null && httpStatus >= 200 && httpStatus < 300,
                httpStatus,
                result);
    }

    private HealthCheckResult checkRuntime(Session session, String appId, HealthCheck healthCheck) {
        String serviceName = sanitizeServiceName(healthCheck.serviceName());
        String command = "container_ids=$(docker ps -aq --filter 'label=com.docker.compose.project=gj_" + appId
                + "' --filter 'label=com.docker.compose.service=" + serviceName + "'); [ -n \"$container_ids\" ] || exit 1;"
                + " docker inspect --format '{{.State.Status}} {{.State.ExitCode}} {{if .State.Health}}{{.State.Health.Status}}{{end}}' $container_ids";
        CommandResult result = sshCommandExecutor.exec(session, command, CURL_TIMEOUT_MS);
        String state = result.stdout() == null ? "" : result.stdout().trim();
        boolean healthy = result.isSuccess() && !state.isEmpty() && state.lines().allMatch(line -> {
            String status = line.trim();
            return status.equals("running 0") || status.equals("running 0 healthy")
                    || Boolean.TRUE.equals(healthCheck.allowCompleted()) && status.equals("exited 0");
        });
        if (healthy && healthCheck.containerPort() != null) {
            String probe = "container_ids=$(docker ps -aq --filter 'label=com.docker.compose.project=gj_" + appId
                    + "' --filter 'label=com.docker.compose.service=" + serviceName + "'); [ -n \"$container_ids\" ] || exit 1;"
                    + " for container_id in $container_ids; do"
                    + " ip=$(docker inspect --format '{{range .NetworkSettings.Networks}}{{.IPAddress}} {{end}}' \"$container_id\" | awk '{print $1}');"
                    + " case \"$ip\" in ''|*[!0-9.]*) exit 1;; esac;"
                    + " timeout 5 bash -c 'exec 3<>/dev/tcp/$1/$2' -- \"$ip\" " + requirePort(healthCheck.containerPort())
                    + " || exit 1; done";
            CommandResult connected = sshCommandExecutor.exec(session, probe, CURL_TIMEOUT_MS);
            return new HealthCheckResult(connected.isSuccess(), null, connected);
        }
        return new HealthCheckResult(healthy, null, result);
    }

    private String containerIdCommand(String appId, String service) {
        return "container_id=$(docker ps -aq --filter 'label=com.docker.compose.project=gj_" + appId
                + "' --filter 'label=com.docker.compose.service=" + service + "' | head -n 1);"
                + " [ -n \"$container_id\" ] || exit 1;";
    }

    private int requirePort(int port) {
        if (port < 1 || port > 65535) throw new OpsException(OpsErrorCode.INVALID_COMPOSE);
        return port;
    }

    private Integer parseHttpStatus(String stdout) {
        String value = stdout == null ? "" : stdout.trim();
        if (!value.matches("\\d{3}")) {
            return null;
        }
        return Integer.parseInt(value);
    }

    private String sanitizeServiceName(String serviceName) {
        if (serviceName == null || !SAFE_SERVICE_NAME.matcher(serviceName).matches()) {
            throw new OpsException(OpsErrorCode.INVALID_COMPOSE);
        }
        return serviceName;
    }

    private String sanitizePath(String path) {
        String value = path != null ? path : "/";
        if (!SAFE_PATH.matcher(value).matches()) {
            throw new OpsException(OpsErrorCode.INVALID_COMPOSE);
        }
        return value;
    }
}
