package gj.cloud.ops.application.backup.service;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import gj.cloud.ops.application.backup.dto.DbBackupRequest;
import gj.cloud.ops.application.backup.dto.PreparedDbBackup;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static gj.cloud.ops.global.ssh.PosixShellArgument.quote;

@Slf4j
@Service
@RequiredArgsConstructor
public class DbBackupService {

    private static final String PERMISSION_DEPLOY = "DEPLOY";
    private static final String PERMISSION_BACKUP_READ = "BACKUP_READ";
    private static final long BACKUP_TIMEOUT_MS = 120_000;
    private static final int SFTP_CONNECT_TIMEOUT_MS = 10_000;
    private static final int MAX_PASSWORD_LENGTH = 4096;
    private static final Set<String> SUPPORTED_DB_TYPES = Set.of("postgresql", "mysql", "redis", "mongodb");
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[A-Za-z0-9._-]+$");

    private final VmServiceClient vmServiceClient;
    private final VmSshSessionFactory sshSessionFactory;
    private final SshCommandExecutor sshCommandExecutor;
    private final DbBackupRepository backupRepository;
    private final BackupFileCipher backupFileCipher;

    @Value("${ops.backup.retention-days:30}")
    private int retentionDays;

    @Value("${ops.backup.max-files-per-vm:20}")
    private int maxFilesPerVm;

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
        validateSecret(password);

        VmContextResponse context = requirePermission(bearerToken, vmId, PERMISSION_DEPLOY);
        Session session = sshSessionFactory.createSession(vmId, context.internalIp());
        String encryptedPath = backupsDir() + "/" + buildFileName(serviceName, dbType);
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(Math.max(1, retentionDays));
        try {
            sshCommandExecutor.execOrThrow(session,
                    "mkdir -p " + quote(backupsDir()) + " && chmod 700 " + quote(backupsDir()), 10_000);

            String checksum = createEncryptedBackup(
                    session, vmId, serviceName, dbType, database, username, password, encryptedPath);
            verifyEncryptedFile(session, encryptedPath, checksum);
            long size = fileSize(session, encryptedPath);
            DbBackupEntity saved = backupRepository.save(DbBackupEntity.succeeded(
                    vmId,
                    serviceName,
                    dbType,
                    encryptedPath,
                    size,
                    checksum,
                    BackupFileCipher.VERSION,
                    LocalDateTime.now(),
                    expiresAt));
            pruneBackups(session, vmId, saved.getId());
            return saved;
        } catch (OpsException e) {
            deleteRemoteFileQuietly(session, encryptedPath);
            backupRepository.save(DbBackupEntity.failed(
                    vmId, serviceName, dbType, OpsErrorCode.DB_BACKUP_FAILED.getMessage(), expiresAt));
            throw e;
        } catch (RuntimeException e) {
            deleteRemoteFileQuietly(session, encryptedPath);
            throw e;
        } finally {
            if (session.isConnected()) {
                session.disconnect();
            }
        }
    }

    public List<DbBackupEntity> history(String bearerToken, String vmId) {
        requirePermission(bearerToken, vmId, PERMISSION_BACKUP_READ);
        return backupRepository.findAllByVmIdOrderByCreatedAtDesc(vmId);
    }

    public PreparedDbBackup prepareDownload(String bearerToken, String vmId, String backupId) {
        VmContextResponse context = requirePermission(bearerToken, vmId, PERMISSION_BACKUP_READ);
        DbBackupEntity backup = requireEncryptedBackup(vmId, backupId);
        return new PreparedDbBackup(
                vmId,
                context.internalIp(),
                backup.getFilePath(),
                plainFileName(backup.getFilePath()),
                backup.getChecksumSha256());
    }

    public void download(PreparedDbBackup backup, OutputStream output) {
        Session session = sshSessionFactory.createSession(backup.vmId(), backup.internalIp());
        try {
            String checksum = decryptRemoteFile(session, backup.filePath(), output);
            if (!checksum.equals(backup.checksumSha256())) {
                throw new OpsException(OpsErrorCode.DB_BACKUP_FAILED);
            }
            log.info("AUDIT action=DB_BACKUP_DOWNLOAD targetType=VM targetId={} path={} result=SUCCESS",
                    backup.vmId(), backup.filePath());
        } finally {
            if (session.isConnected()) {
                session.disconnect();
            }
        }
    }

    public DbBackupEntity verify(String bearerToken, String vmId, String backupId) {
        VmContextResponse context = requirePermission(bearerToken, vmId, PERMISSION_BACKUP_READ);
        DbBackupEntity backup = requireEncryptedBackup(vmId, backupId);
        Session session = sshSessionFactory.createSession(vmId, context.internalIp());
        try {
            verifyEncryptedFile(session, backup.getFilePath(), backup.getChecksumSha256());
            backup.markVerified(LocalDateTime.now());
            return backupRepository.save(backup);
        } finally {
            if (session.isConnected()) {
                session.disconnect();
            }
        }
    }

    private VmContextResponse requirePermission(String bearerToken, String vmId, String permission) {
        VmContextResponse context = vmServiceClient.getContext(bearerToken, vmId);
        if (!context.hasPermission(permission)) {
            throw new OpsException(OpsErrorCode.FORBIDDEN);
        }
        if (context.internalIp() == null || !"RUNNING".equals(context.status())) {
            throw new OpsException(OpsErrorCode.VM_NOT_RUNNING);
        }
        return context;
    }

    private DbBackupEntity requireEncryptedBackup(String vmId, String backupId) {
        DbBackupEntity backup = backupRepository.findByIdAndVmId(backupId, vmId)
                .orElseThrow(() -> new OpsException(OpsErrorCode.FILE_NOT_FOUND));
        if (!backup.isSucceeded()
                || backup.getFilePath() == null
                || !BackupFileCipher.VERSION.equals(backup.getEncryptionVersion())
                || backup.getChecksumSha256() == null) {
            throw new OpsException(OpsErrorCode.DB_BACKUP_FAILED);
        }
        return backup;
    }

    private String createEncryptedBackup(
            Session session,
            String vmId,
            String service,
            String dbType,
            String database,
            String username,
            String password,
            String encryptedPath
    ) {
        ChannelSftp sftp = null;
        try {
            sftp = openSftp(session);
            BackupFileCipher.EncryptionWriter writer;
            try (OutputStream remoteFile = sftp.put(encryptedPath)) {
                writer = backupFileCipher.encrypting(remoteFile);
                try (writer) {
                    runBackup(session, vmId, service, dbType, database, username, password, writer.outputStream());
                }
            }
            sftp.chmod(0600, encryptedPath);
            return writer.checksumSha256();
        } catch (JSchException | SftpException | IOException e) {
            log.error("백업 암호화 스트리밍 실패: vmId={}, service={}, dbType={}, error={}",
                    vmId, service, dbType, e.getMessage());
            throw new OpsException(OpsErrorCode.DB_BACKUP_FAILED);
        } finally {
            disconnect(sftp);
        }
    }

    private void runBackup(
            Session session,
            String vmId,
            String service,
            String dbType,
            String database,
            String username,
            String password,
            OutputStream output
    ) {
        String project = "gj_" + vmId;
        if ("redis".equals(dbType)) {
            sshCommandExecutor.execOrThrow(session,
                    "docker compose -p " + quote(project) + " exec -T " + quote(service) + " redis-cli SAVE",
                    BACKUP_TIMEOUT_MS);
            executeStreamingBackup(session,
                    "docker compose -p " + quote(project) + " exec -T " + quote(service) + " cat /data/dump.rdb",
                    output, vmId, service, dbType);
            return;
        }

        if ("postgresql".equals(dbType) || "mysql".equals(dbType)) {
            backupRelational(session, vmId, project, service, dbType, database, username, password, output);
            return;
        }

        executeStreamingBackup(session,
                "docker compose -p " + quote(project) + " exec -T " + quote(service)
                        + " mongodump --archive --db=" + quote(database),
                output, vmId, service, dbType);
    }

    private void backupRelational(
            Session session,
            String vmId,
            String project,
            String service,
            String dbType,
            String database,
            String username,
            String password,
            OutputStream output
    ) {
        String credentialId = UUID.randomUUID().toString();
        String hostCredentialPath = backupsDir() + "/.credential-" + credentialId;
        String containerCredentialPath = "/tmp/gj-db-backup-" + credentialId;
        String containerId = resolveContainerId(session, project, service);
        byte[] credentialBytes = password == null ? null : credentialContent(dbType, password);
        boolean copiedToContainer = false;

        try {
            if (credentialBytes != null) {
                writeSensitiveFile(session, hostCredentialPath, credentialBytes);
                sshCommandExecutor.execOrThrow(session,
                        "docker cp " + quote(hostCredentialPath) + " "
                                + quote(containerId + ":" + containerCredentialPath), 10_000);
                copiedToContainer = true;
                sshCommandExecutor.execOrThrow(session,
                        "docker exec -u 0 " + quote(containerId) + " chmod 600 " + quote(containerCredentialPath),
                        10_000);
            }

            String command;
            if ("postgresql".equals(dbType)) {
                command = "docker compose -p " + quote(project) + " exec -T"
                        + (credentialBytes != null ? " -u 0" : "")
                        + (credentialBytes != null ? " -e PGPASSFILE=" + quote(containerCredentialPath) : "")
                        + " " + quote(service) + " pg_dump -U " + quote(username) + " " + quote(database);
            } else {
                command = "docker compose -p " + quote(project) + " exec -T"
                        + (credentialBytes != null ? " -u 0" : "")
                        + " " + quote(service) + " mysqldump"
                        + (credentialBytes != null ? " --defaults-extra-file=" + quote(containerCredentialPath) : "")
                        + " -u " + quote(username) + " " + quote(database);
            }
            executeStreamingBackup(session, command, output, vmId, service, dbType);
        } finally {
            if (credentialBytes != null) {
                Arrays.fill(credentialBytes, (byte) 0);
            }
            if (copiedToContainer) {
                sshCommandExecutor.exec(session,
                        "docker exec -u 0 " + quote(containerId) + " rm -f " + quote(containerCredentialPath),
                        10_000);
            }
            deleteRemoteFileQuietly(session, hostCredentialPath);
        }
    }

    private void executeStreamingBackup(
            Session session,
            String command,
            OutputStream output,
            String vmId,
            String service,
            String dbType
    ) {
        long timeoutSeconds = BACKUP_TIMEOUT_MS / 1000;
        String boundedCommand = "timeout --signal=TERM --kill-after=5s " + timeoutSeconds + "s sh -c " + quote(command);
        CommandResult result = sshCommandExecutor.execTo(session, boundedCommand, output, BACKUP_TIMEOUT_MS + 10_000);
        if (!result.isSuccess()) {
            log.error("DB 백업 실패: vmId={}, service={}, dbType={}, stderr={}",
                    vmId, service, dbType, result.stderr());
            throw new OpsException(OpsErrorCode.DB_BACKUP_FAILED);
        }
    }

    private void verifyEncryptedFile(Session session, String path, String expectedChecksum) {
        String actual = decryptRemoteFile(session, path, OutputStream.nullOutputStream());
        if (!actual.equals(expectedChecksum)) {
            throw new OpsException(OpsErrorCode.DB_BACKUP_FAILED);
        }
    }

    private String decryptRemoteFile(Session session, String path, OutputStream output) {
        ChannelSftp sftp = null;
        try {
            sftp = openSftp(session);
            try (InputStream encrypted = sftp.get(path)) {
                return backupFileCipher.decrypt(encrypted, output);
            }
        } catch (JSchException | SftpException | IOException e) {
            log.error("백업 복호화/무결성 검증 실패: error={}", e.getMessage());
            throw new OpsException(OpsErrorCode.DB_BACKUP_FAILED);
        } finally {
            disconnect(sftp);
        }
    }

    private void pruneBackups(Session session, String vmId, String currentBackupId) {
        List<DbBackupEntity> backups = backupRepository.findAllByVmIdOrderByCreatedAtDesc(vmId);
        LocalDateTime now = LocalDateTime.now();
        int successfulFilesKept = 0;
        for (DbBackupEntity backup : backups) {
            if (backup.getId().equals(currentBackupId)) {
                successfulFilesKept++;
                continue;
            }
            boolean expired = backup.getExpiresAt() != null && backup.getExpiresAt().isBefore(now);
            if (backup.isSucceeded()) {
                successfulFilesKept++;
            }
            boolean overCount = backup.isSucceeded() && successfulFilesKept > Math.max(1, maxFilesPerVm);
            if (!expired && !overCount) {
                continue;
            }
            if (backup.getFilePath() == null || deleteRemoteFileQuietly(session, backup.getFilePath())) {
                backupRepository.delete(backup);
            }
        }
    }

    private String resolveContainerId(Session session, String project, String service) {
        CommandResult result = sshCommandExecutor.execOrThrow(session,
                "docker compose -p " + quote(project) + " ps -q " + quote(service), 10_000);
        String containerId = result.stdout().trim();
        if (!containerId.matches("^[a-fA-F0-9]{12,64}$")) {
            throw new OpsException(OpsErrorCode.DB_BACKUP_FAILED);
        }
        return containerId;
    }

    private byte[] credentialContent(String dbType, String password) {
        String content;
        if ("postgresql".equals(dbType)) {
            String escaped = password.replace("\\", "\\\\").replace(":", "\\:");
            content = "*:*:*:*:" + escaped + "\n";
        } else {
            String escaped = password.replace("\\", "\\\\").replace("\"", "\\\"");
            content = "[client]\npassword=\"" + escaped + "\"\n";
        }
        return content.getBytes(StandardCharsets.UTF_8);
    }

    private void writeSensitiveFile(Session session, String path, byte[] content) {
        ChannelSftp sftp = null;
        try {
            sftp = openSftp(session);
            sftp.put(new ByteArrayInputStream(content), path);
            sftp.chmod(0600, path);
        } catch (JSchException | SftpException e) {
            throw new OpsException(OpsErrorCode.DB_BACKUP_FAILED);
        } finally {
            disconnect(sftp);
        }
    }

    private boolean deleteRemoteFileQuietly(Session session, String path) {
        ChannelSftp sftp = null;
        try {
            sftp = openSftp(session);
            sftp.rm(path);
            return true;
        } catch (SftpException e) {
            return e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE;
        } catch (JSchException e) {
            return false;
        } finally {
            disconnect(sftp);
        }
    }

    private ChannelSftp openSftp(Session session) throws JSchException {
        ChannelSftp sftp = (ChannelSftp) session.openChannel("sftp");
        sftp.connect(SFTP_CONNECT_TIMEOUT_MS);
        return sftp;
    }

    private void disconnect(ChannelSftp sftp) {
        if (sftp != null && sftp.isConnected()) {
            sftp.disconnect();
        }
    }

    private long fileSize(Session session, String path) {
        CommandResult result = sshCommandExecutor.exec(session, "stat -c %s " + quote(path), 10_000);
        if (!result.isSuccess()) {
            throw new OpsException(OpsErrorCode.DB_BACKUP_FAILED);
        }
        try {
            return Long.parseLong(result.stdout().trim());
        } catch (NumberFormatException e) {
            throw new OpsException(OpsErrorCode.DB_BACKUP_FAILED);
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
        return service + "_" + timestamp + "_" + UUID.randomUUID().toString().substring(0, 8)
                + "." + extension + ".enc";
    }

    private String plainFileName(String encryptedPath) {
        String name = encryptedPath.substring(encryptedPath.lastIndexOf('/') + 1);
        return name.endsWith(".enc") ? name.substring(0, name.length() - 4) : name;
    }

    private String sanitize(String identifier) {
        if (identifier == null || !SAFE_IDENTIFIER.matcher(identifier).matches()) {
            throw new OpsException(OpsErrorCode.INVALID_DB_IDENTIFIER);
        }
        return identifier;
    }

    private void validateSecret(String secret) {
        if (secret == null) {
            return;
        }
        boolean containsControlCharacter = secret.codePoints().anyMatch(codePoint ->
                Character.isISOControl(codePoint) || codePoint == 0x2028 || codePoint == 0x2029);
        if (secret.length() > MAX_PASSWORD_LENGTH || containsControlCharacter) {
            throw new OpsException(OpsErrorCode.INVALID_DB_IDENTIFIER);
        }
    }
}
