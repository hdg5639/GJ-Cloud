package gj.cloud.ops.application.backup.dto;

import gj.cloud.ops.domain.backup.entity.DbBackupEntity;

import java.time.LocalDateTime;

// filePath는 VM 파일시스템 절대경로 — 파일 브라우저(/api/ops/{vmId}/files/download?path=...)로 그대로 다운로드 가능
public record DbBackupResponse(
        String id,
        String vmId,
        String serviceName,
        String dbType,
        String filePath,
        Long fileSizeBytes,
        boolean succeeded,
        String errorMessage,
        LocalDateTime createdAt
) {
    public static DbBackupResponse from(DbBackupEntity entity) {
        return new DbBackupResponse(
                entity.getId(), entity.getVmId(), entity.getServiceName(), entity.getDbType(),
                entity.getFilePath(), entity.getFileSizeBytes(), entity.isSucceeded(),
                entity.getErrorMessage(), entity.getCreatedAt()
        );
    }
}
