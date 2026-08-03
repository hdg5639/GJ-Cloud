package gj.cloud.ops.application.backup.dto;

public record PreparedDbBackup(
        String vmId,
        String internalIp,
        String filePath,
        String fileName,
        String checksumSha256
) {
}
