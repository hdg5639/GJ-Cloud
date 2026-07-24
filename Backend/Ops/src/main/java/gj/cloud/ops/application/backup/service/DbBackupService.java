package gj.cloud.ops.application.backup.service;

import com.jcraft.jsch.Session;
import gj.cloud.ops.application.backup.dto.DbBackupRequest;
import gj.cloud.ops.application.vmclient.VmServiceClient;
import gj.cloud.ops.application.vmclient.dto.VmContextResponse;
import gj.cloud.ops.domain.backup.entity.DbBackupEntity;
import gj.cloud.ops.domain.backup.repository.DbBackupRepository;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import gj.cloud.ops.global.ssh.CommandResult;
import gj.cloud.ops.global.ssh.SshCommandExecutor;
import gj.cloud.ops.global.ssh.VmSshSessionFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

// 11절 수동 DB 백업 — 사용자가 트리거할 때만 실행되는 온디맨드 백업(자동/정기 백업은 범위 밖).
// 덤프 결과는 compose exec의 stdout을 SSH 셸 리다이렉트로 VM 호스트 파일에 직접 기록해 gamjabox DB에는 절대 흘러들지 않음.
// redis만 컨테이너 내부에 파일이 생성되므로 docker cp로 호스트에 복사하는 2단계가 필요함.
@Slf4j
@Service
@RequiredArgsConstructor
public class DbBackupService {

    private static final String PERMISSION_DEPLOY = "DEPLOY";
    private static final long BACKUP_TIMEOUT_MS = 120_000;
    private static final Set<String> SUPPORTED_DB_TYPES = Set.of("postgresql", "mysql", "redis", "mongodb");
    // 서비스명/DB명/사용자명은 셸 커맨드에 그대로 꽂히므로 반드시 검증 (DockerService와 동일한 원칙)
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[A-Za-z0-9._-]+$");

    private final VmServiceClient vmServiceClient;
    private final VmSshSessionFactory sshSessionFactory;
    private final SshCommandExecutor sshCommandExecutor;
    private final DbBackupRepository backupRepository;

    public DbBackupEntity backup(String bearerToken, String vmId, DbBackupRequest request) {
        String serviceName = sanitize(request.serviceName());
        String database = sanitize(request.database());
        String dbType = request.dbType();
        if (!SUPPORTED_DB_TYPES.contains(dbType)) {
            throw new OpsException(OpsErrorCode.INVALID_DB_IDENTIFIER);
        }
        String username = request.username() != null ? sanitize(request.username()) : null;
        if (("postgresql".equals(dbType) || "mysql".equals(dbType)) && username == null) {
            throw new OpsException(OpsErrorCode.INVALID_DB_IDENTIFIER);
        }
        String password = request.password();
        if (password != null && password.contains("'")) {
            throw new OpsException(OpsErrorCode.INVALID_DB_IDENTIFIER);
        }

        VmContextResponse context = vmServiceClient.getContext(bearerToken, vmId);
        if (!context.hasPermission(PERMISSION_DEPLOY)) {
            throw new OpsException(OpsErrorCode.FORBIDDEN);
        }
        if (context.internalIp() == null || !"RUNNING".equals(context.status())) {
            throw new OpsException(OpsErrorCode.VM_NOT_RUNNING);
        }

        Session session = sshSessionFactory.createSession(vmId, context.internalIp());
        try {
            String hostFilePath = backupsDir() + "/" + buildFileName(serviceName, dbType);
            sshCommandExecutor.execOrThrow(session, "mkdir -p '" + backupsDir() + "'", 10_000);

            runBackup(session, vmId, serviceName, dbType, database, username, password, hostFilePath);

            long size = fileSize(session, hostFilePath);
            return backupRepository.save(
                    DbBackupEntity.create(vmId, serviceName, dbType, hostFilePath, size, true, null));
        } catch (OpsException e) {
            backupRepository.save(DbBackupEntity.create(vmId, serviceName, dbType, null, null, false, e.getMessage()));
            throw e;
        } finally {
            if (session.isConnected()) {
                session.disconnect();
            }
        }
    }

    public List<DbBackupEntity> history(String bearerToken, String vmId) {
        VmContextResponse context = vmServiceClient.getContext(bearerToken, vmId);
        if (!context.hasPermission(PERMISSION_DEPLOY)) {
            throw new OpsException(OpsErrorCode.FORBIDDEN);
        }
        return backupRepository.findAllByVmIdOrderByCreatedAtDesc(vmId);
    }

    private void runBackup(Session session, String vmId, String service, String dbType, String database,
                           String username, String password, String hostFilePath) {
        String project = "gj_" + vmId;

        if ("redis".equals(dbType)) {
            backupRedis(session, project, service, hostFilePath);
            return;
        }

        String command = switch (dbType) {
            case "postgresql" -> "docker compose -p " + project + " exec -T"
                    + (password != null ? " -e PGPASSWORD='" + password + "'" : "")
                    + " " + service + " pg_dump -U '" + username + "' '" + database + "' > '" + hostFilePath + "'";
            case "mysql" -> "docker compose -p " + project + " exec -T"
                    + (password != null ? " -e MYSQL_PWD='" + password + "'" : "")
                    + " " + service + " mysqldump -u '" + username + "' '" + database + "' > '" + hostFilePath + "'";
            case "mongodb" -> "docker compose -p " + project + " exec -T " + service
                    + " mongodump --archive --db='" + database + "' > '" + hostFilePath + "'";
            default -> throw new OpsException(OpsErrorCode.INVALID_DB_IDENTIFIER);
        };

        CommandResult result = sshCommandExecutor.exec(session, command, BACKUP_TIMEOUT_MS);
        if (!result.isSuccess()) {
            log.error("DB 백업 실패: vmId={}, service={}, dbType={}, stderr={}", vmId, service, dbType, result.stderr());
            throw new OpsException(OpsErrorCode.DB_BACKUP_FAILED);
        }
    }

    // redis는 SAVE가 컨테이너 내부(/data/dump.rdb)에만 파일을 만들기 때문에 stdout 리다이렉트를 쓸 수 없어 docker cp로 한 단계 더 필요
    private void backupRedis(Session session, String project, String service, String hostFilePath) {
        sshCommandExecutor.execOrThrow(session,
                "docker compose -p " + project + " exec -T " + service + " redis-cli SAVE", BACKUP_TIMEOUT_MS);
        CommandResult idResult = sshCommandExecutor.execOrThrow(session,
                "docker compose -p " + project + " ps -q " + service, 10_000);
        String containerId = idResult.stdout().trim();
        if (containerId.isEmpty()) {
            throw new OpsException(OpsErrorCode.DB_BACKUP_FAILED);
        }
        sshCommandExecutor.execOrThrow(session,
                "docker cp " + containerId + ":/data/dump.rdb '" + hostFilePath + "'", BACKUP_TIMEOUT_MS);
    }

    private long fileSize(Session session, String hostFilePath) {
        CommandResult result = sshCommandExecutor.exec(session, "stat -c %s '" + hostFilePath + "'", 10_000);
        if (!result.isSuccess()) {
            return 0L;
        }
        try {
            return Long.parseLong(result.stdout().trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private String backupsDir() {
        return "/home/" + sshSessionFactory.vmSshUsername() + "/gamjabox/backups";
    }

    private String buildFileName(String service, String dbType) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String extension = switch (dbType) {
            case "postgresql", "mysql" -> "sql";
            case "mongodb" -> "archive";
            case "redis" -> "rdb";
            default -> "bak";
        };
        return service + "_" + timestamp + "." + extension;
    }

    private String sanitize(String identifier) {
        if (identifier == null || !SAFE_IDENTIFIER.matcher(identifier).matches()) {
            throw new OpsException(OpsErrorCode.INVALID_DB_IDENTIFIER);
        }
        return identifier;
    }
}
