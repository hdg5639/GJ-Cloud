package gj.cloud.ops.application.systemworker;

import com.jcraft.jsch.Session;
import gj.cloud.ops.domain.systemworker.entity.SystemWorkerEntity;
import gj.cloud.ops.global.ssh.SshCommandExecutor;
import gj.cloud.ops.global.ssh.VmSshSessionFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;

@Service
@RequiredArgsConstructor
public class SystemWorkerRuntimeService {
    private static final long INSTALL_TIMEOUT_MS = 600_000;
    private static final String REPAIR = "sudo sh -lc 'set -eu; export DEBIAN_FRONTEND=noninteractive; "
            + "mkdir -p /opt/gamjabox/previews; "
            + "if ! command -v docker >/dev/null 2>&1; then "
            + "apt-get update; apt-get install -y ca-certificates curl; install -m 0755 -d /etc/apt/keyrings; "
            + "curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc; "
            + "chmod a+r /etc/apt/keyrings/docker.asc; "
            + ". /etc/os-release; echo \"deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $VERSION_CODENAME stable\" > /etc/apt/sources.list.d/docker.list; "
            + "apt-get update; apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin; fi; "
            + "systemctl enable --now docker; docker network inspect gamjabox-preview >/dev/null 2>&1 || docker network create gamjabox-preview >/dev/null'";

    private final VmSshSessionFactory sessionFactory;
    private final SshCommandExecutor commands;

    public boolean healthy(SystemWorkerEntity worker) {
        if (worker.getInternalIp() == null) return false;
        return sessionFactory.tryCreateSession(worker.getSshKeyRef(), worker.getInternalIp()).map(session -> {
            try {
                return commands.exec(session, "docker info >/dev/null 2>&1 && test -d /opt/gamjabox/previews && docker network inspect gamjabox-preview >/dev/null 2>&1", 20_000).isSuccess();
            } finally { session.disconnect(); }
        }).orElse(false);
    }

    public void waitUntilReachable(SystemWorkerEntity worker) {
        for (int attempt = 0; attempt < 120; attempt++) {
            var session = sessionFactory.tryCreateSession(worker.getSshKeyRef(), worker.getInternalIp());
            if (session.isPresent()) {
                session.get().disconnect();
                return;
            }
            try { Thread.sleep(5_000); }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new OpsException(OpsErrorCode.SSH_CONNECTION_FAILED);
            }
        }
        throw new OpsException(OpsErrorCode.SSH_CONNECTION_FAILED);
    }

    public void repair(SystemWorkerEntity worker) {
        Session session = sessionFactory.createSession(worker.getSshKeyRef(), worker.getInternalIp());
        try { commands.execOrThrow(session, REPAIR, INSTALL_TIMEOUT_MS); }
        finally { session.disconnect(); }
    }
}
