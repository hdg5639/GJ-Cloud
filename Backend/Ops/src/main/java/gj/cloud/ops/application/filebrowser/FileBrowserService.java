package gj.cloud.ops.application.filebrowser;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;
import gj.cloud.ops.application.filebrowser.dto.FileContentResponse;
import gj.cloud.ops.application.filebrowser.dto.FileEntry;
import gj.cloud.ops.application.filebrowser.dto.FileListResponse;
import gj.cloud.ops.application.vmclient.VmServiceClient;
import gj.cloud.ops.application.vmclient.dto.VmContextResponse;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import gj.cloud.ops.global.ssh.VmSshSessionFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// SFTP 기반 파일 브라우저. 접근 가능 루트는 SftpPathResolver가 /home/{vmSshUsername} 이하로 강제하고,
// 매 작업 전 VmServiceClient로 FILE_READ/FILE_WRITE 권한을 확인함 (Owner/Admin=전체, Member=조회만).
@Slf4j
@Service
@RequiredArgsConstructor
public class FileBrowserService {

    private static final String PERMISSION_FILE_READ = "FILE_READ";
    private static final String PERMISSION_FILE_WRITE = "FILE_WRITE";
    private static final String PERMISSION_SECRET_READ = "SECRET_READ";
    private static final int SFTP_CONNECT_TIMEOUT_MS = 10_000;
    private static final int BINARY_SNIFF_LENGTH = 8000;

    private final VmServiceClient vmServiceClient;
    private final VmSshSessionFactory sshSessionFactory;
    private final SftpPathResolver pathResolver;

    @Value("${ops.file-browser-max-edit-size-bytes:1048576}")
    private long maxEditSizeBytes;

    @Value("${ops.file-browser-max-upload-size-bytes:26214400}")
    private long maxUploadSizeBytes;

    public FileListResponse list(String bearerToken, String vmId, String path) {
        return execute(bearerToken, vmId, PERMISSION_FILE_READ, (sftp, context) -> {
            String real = pathResolver.resolveExisting(sftp, path);
            requireNotBackupPath(real);
            List<FileEntry> entries = new ArrayList<>();
            for (Object o : sftp.ls(real)) {
                ChannelSftp.LsEntry lsEntry = (ChannelSftp.LsEntry) o;
                String name = lsEntry.getFilename();
                if (".".equals(name) || "..".equals(name)) {
                    continue;
                }
                SftpATTRS attrs = lsEntry.getAttrs();
                entries.add(new FileEntry(name, joinPath(real, name), attrs.isDir(), attrs.getSize(),
                        toLocalDateTime(attrs.getMTime())));
            }
            entries.sort(Comparator.comparing(FileEntry::directory).reversed().thenComparing(FileEntry::name));
            return new FileListResponse(real, entries);
        });
    }

    public FileContentResponse readContent(String bearerToken, String vmId, String path) {
        return execute(bearerToken, vmId, PERMISSION_FILE_READ, (sftp, context) -> {
            String real = pathResolver.resolveExisting(sftp, path);
            requireNotBackupPath(real);
            requireSecretReadIfSensitive(context, real);
            SftpATTRS attrs = statOrThrow(sftp, real);
            if (attrs.isDir()) {
                throw new OpsException(OpsErrorCode.INVALID_PATH);
            }
            if (attrs.getSize() > maxEditSizeBytes) {
                throw new OpsException(OpsErrorCode.FILE_TOO_LARGE);
            }

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try {
                sftp.get(real, buffer);
            } catch (SftpException e) {
                throw new OpsException(OpsErrorCode.SFTP_OPERATION_FAILED);
            }
            byte[] content = buffer.toByteArray();
            if (isBinary(content)) {
                throw new OpsException(OpsErrorCode.BINARY_FILE_EDIT_FORBIDDEN);
            }
            return new FileContentResponse(real, new String(content, StandardCharsets.UTF_8));
        });
    }

    public void writeContent(String bearerToken, String vmId, String path, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maxEditSizeBytes) {
            throw new OpsException(OpsErrorCode.FILE_TOO_LARGE);
        }
        execute(bearerToken, vmId, PERMISSION_FILE_WRITE, (sftp, context) -> {
            String real = pathResolver.resolveExisting(sftp, path);
            requireNotBackupPath(real);
            try {
                sftp.put(new ByteArrayInputStream(bytes), real);
            } catch (SftpException e) {
                throw new OpsException(OpsErrorCode.SFTP_OPERATION_FAILED);
            }
            return null;
        });
    }

    public void createDirectory(String bearerToken, String vmId, String parentPath, String name) {
        execute(bearerToken, vmId, PERMISSION_FILE_WRITE, (sftp, context) -> {
            String target = pathResolver.resolveNewEntry(sftp, parentPath, name);
            requireNotBackupPath(target);
            try {
                sftp.mkdir(target);
            } catch (SftpException e) {
                throw new OpsException(OpsErrorCode.SFTP_OPERATION_FAILED);
            }
            return null;
        });
    }

    public void upload(String bearerToken, String vmId, String parentPath, String fileName, InputStream content, long size) {
        if (size > maxUploadSizeBytes) {
            throw new OpsException(OpsErrorCode.FILE_TOO_LARGE);
        }
        execute(bearerToken, vmId, PERMISSION_FILE_WRITE, (sftp, context) -> {
            String target = pathResolver.resolveNewEntry(sftp, parentPath, fileName);
            requireNotBackupPath(target);
            try {
                sftp.put(content, target);
            } catch (SftpException e) {
                throw new OpsException(OpsErrorCode.SFTP_OPERATION_FAILED);
            }
            return null;
        });
    }

    public void download(String bearerToken, String vmId, String path, OutputStream out) {
        execute(bearerToken, vmId, PERMISSION_FILE_READ, (sftp, context) -> {
            String real = pathResolver.resolveExisting(sftp, path);
            requireNotBackupPath(real);
            requireSecretReadIfSensitive(context, real);
            SftpATTRS attrs = statOrThrow(sftp, real);
            if (attrs.isDir()) {
                throw new OpsException(OpsErrorCode.INVALID_PATH);
            }
            try {
                sftp.get(real, out);
            } catch (SftpException e) {
                throw new OpsException(OpsErrorCode.SFTP_OPERATION_FAILED);
            }
            return null;
        });
    }

    public void delete(String bearerToken, String vmId, String path) {
        execute(bearerToken, vmId, PERMISSION_FILE_WRITE, (sftp, context) -> {
            String real = pathResolver.resolveExisting(sftp, path);
            requireNotBackupPath(real);
            if (real.equals(pathResolver.rootPath())) {
                throw new OpsException(OpsErrorCode.PATH_ACCESS_DENIED);
            }
            deleteRecursive(sftp, real);
            return null;
        });
    }

    private void requireSecretReadIfSensitive(VmContextResponse context, String resolvedPath) {
        if (SensitiveFilePolicy.isSensitive(resolvedPath) && !context.hasPermission(PERMISSION_SECRET_READ)) {
            // OBS-001: SECRET_READ 없이 민감 파일 내용을 시도한 거부 이벤트 — 구조화 로그 라인으로 기록
            log.warn("AUDIT action=SECRET_FILE_ACCESS_DENIED targetType=VM targetId={} role={} path={} result=DENIED",
                    context.vmId(), context.role(), resolvedPath);
            throw new OpsException(OpsErrorCode.FORBIDDEN);
        }
    }

    private void requireNotBackupPath(String resolvedPath) {
        if (SensitiveFilePolicy.isBackupPath(resolvedPath)) {
            throw new OpsException(OpsErrorCode.FORBIDDEN);
        }
    }

    private void deleteRecursive(ChannelSftp sftp, String path) throws SftpException {
        SftpATTRS attrs = sftp.lstat(path);
        if (attrs.isDir()) {
            for (Object o : sftp.ls(path)) {
                ChannelSftp.LsEntry lsEntry = (ChannelSftp.LsEntry) o;
                String name = lsEntry.getFilename();
                if (".".equals(name) || "..".equals(name)) {
                    continue;
                }
                deleteRecursive(sftp, path + "/" + name);
            }
            sftp.rmdir(path);
        } else {
            sftp.rm(path);
        }
    }

    private SftpATTRS statOrThrow(ChannelSftp sftp, String path) {
        try {
            return sftp.lstat(path);
        } catch (SftpException e) {
            throw new OpsException(OpsErrorCode.FILE_NOT_FOUND);
        }
    }

    private <T> T execute(String bearerToken, String vmId, String requiredPermission, SftpOperation<T> operation) {
        VmContextResponse context = vmServiceClient.getContext(bearerToken, vmId);
        if (!context.hasPermission(requiredPermission)) {
            throw new OpsException(OpsErrorCode.FORBIDDEN);
        }
        if (context.internalIp() == null || !"RUNNING".equals(context.status())) {
            throw new OpsException(OpsErrorCode.VM_NOT_RUNNING);
        }

        Session session = sshSessionFactory.createSession(vmId, context.internalIp());
        ChannelSftp sftp = null;
        try {
            sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect(SFTP_CONNECT_TIMEOUT_MS);
            return operation.apply(sftp, context);
        } catch (JSchException | SftpException e) {
            log.error("SFTP 작업 실패: vmId={}, error={}", vmId, e.getMessage());
            throw new OpsException(OpsErrorCode.SFTP_OPERATION_FAILED);
        } finally {
            if (sftp != null && sftp.isConnected()) {
                sftp.disconnect();
            }
            if (session.isConnected()) {
                session.disconnect();
            }
        }
    }

    private boolean isBinary(byte[] content) {
        int checkLength = Math.min(content.length, BINARY_SNIFF_LENGTH);
        for (int i = 0; i < checkLength; i++) {
            if (content[i] == 0) {
                return true;
            }
        }
        return false;
    }

    private String joinPath(String parent, String name) {
        return parent.endsWith("/") ? parent + name : parent + "/" + name;
    }

    private LocalDateTime toLocalDateTime(int epochSeconds) {
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneId.systemDefault());
    }

    @FunctionalInterface
    private interface SftpOperation<T> {
        T apply(ChannelSftp sftp, VmContextResponse context) throws SftpException;
    }
}
