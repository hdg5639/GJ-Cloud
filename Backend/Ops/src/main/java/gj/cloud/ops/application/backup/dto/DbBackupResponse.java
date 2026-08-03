package gj.cloud.ops.application.backup.dto;

import gj.cloud.ops.domain.backup.entity.DbBackupEntity;

import java.time.LocalDateTime;

// 물리 경로는 노출하지 않고 백업 id 기반 전용 API로만 다운로드한다.
public record DbBackupResponse(
        String id,
        String vmId,
        String serviceName,
        String dbType,
        Long fileSizeBytes,
        String checksumSha256,
        String encryptionVersion,
        LocalDateTime verifiedAt,
        LocalDateTime expiresAt,
        boolean succeeded,
        String errorMessage,
        LocalDateTime createdAt
) {
    public static DbBackupResponse from(DbBackupEntity entity) {
        return new DbBackupResponse(
                entity.getId(), entity.getVmId(), entity.getServiceName(), entity.getDbType(),
                entity.getFileSizeBytes(), entity.getChecksumSha256(), entity.getEncryptionVersion(),
                entity.getVerifiedAt(), entity.getExpiresAt(), entity.isSucceeded(),
                entity.getErrorMessage(), entity.getCreatedAt()
        );
    }
}
