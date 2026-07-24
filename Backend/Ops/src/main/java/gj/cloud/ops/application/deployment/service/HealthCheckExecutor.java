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
// compose 네트워크 내부에서만 유효하므로, 대상 컨테이너 자신 안에서 curl(localhost)로 실행함
// (docker compose exec 사용 — 대상 이미지에 curl이 설치돼 있어야 하는 제약이 있음, 알려진 한계).
@Component
@RequiredArgsConstructor
public class HealthCheckExecutor {

    private static final long CURL_TIMEOUT_MS = 15_000;
    // serviceName/path 모두 ComposeArtifact를 통해 들어오는 사용자 입력이라, 셸 커맨드에 꽂기 전에 반드시 검증함
    private static final Pattern SAFE_SERVICE_NAME = Pattern.compile("^[A-Za-z0-9_.-]+$");
    private static final Pattern SAFE_PATH = Pattern.compile("^/[A-Za-z0-9_./-]*$");

    private final SshCommandExecutor sshCommandExecutor;

    public boolean check(Session session, String appId, HealthCheck healthCheck) {
        String path = sanitizePath(healthCheck.path());
        String command;
        if (healthCheck.hostPort() != null) {
            command = "curl -s -o /dev/null -w '%{http_code}' --max-time 10 'http://127.0.0.1:"
                    + healthCheck.hostPort() + path + "'";
        } else if (healthCheck.containerPort() != null) {
            String serviceName = sanitizeServiceName(healthCheck.serviceName());
            command = "docker compose -p gj_" + appId + " exec -T '" + serviceName + "'"
                    + " curl -s -o /dev/null -w '%{http_code}' --max-time 10 'http://localhost:"
                    + healthCheck.containerPort() + path + "'";
        } else {
            return false;
        }

        CommandResult result = sshCommandExecutor.exec(session, command, CURL_TIMEOUT_MS);
        if (!result.isSuccess()) {
            return false;
        }
        return result.stdout().trim().startsWith("2");
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
