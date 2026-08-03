package gj.cloud.ops.domain.backup.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

// 수동 DB 백업 이력 — VM backups/ 경로에는 AES-GCM 암호문만 저장하고 여기에는
// 체크섬·암호화 버전·검증/만료 시각을 기록한다. 평문 다운로드는 BACKUP_READ 전용 API로만 제공한다.
@Entity
@Table(name = "db_backups")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class DbBackupEntity {

    @Id
    private String id;

    @Column(name = "vm_id", nullable = false)
    private String vmId;

    @Column(name = "service_name", nullable = false)
    private String serviceName;

    @Column(name = "db_type", nullable = false)
    private String dbType;

    @Column(name = "file_path", columnDefinition = "TEXT")
    private String filePath;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    @Column(name = "encryption_version", length = 30)
    private String encryptionVersion;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean succeeded;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static DbBackupEntity succeeded(
            String vmId,
            String serviceName,
            String dbType,
            String filePath,
            Long fileSizeBytes,
            String checksumSha256,
            String encryptionVersion,
            LocalDateTime verifiedAt,
            LocalDateTime expiresAt
    ) {
        return DbBackupEntity.builder()
                .id(UUID.randomUUID().toString())
                .vmId(vmId)
                .serviceName(serviceName)
                .dbType(dbType)
                .filePath(filePath)
                .fileSizeBytes(fileSizeBytes)
                .checksumSha256(checksumSha256)
                .encryptionVersion(encryptionVersion)
                .verifiedAt(verifiedAt)
                .expiresAt(expiresAt)
                .succeeded(true)
                .createdAt(LocalDateTime.now())
                .build();
    }

    public static DbBackupEntity failed(
            String vmId, String serviceName, String dbType, String errorMessage, LocalDateTime expiresAt
    ) {
        return DbBackupEntity.builder()
                .id(UUID.randomUUID().toString())
                .vmId(vmId)
                .serviceName(serviceName)
                .dbType(dbType)
                .succeeded(false)
                .errorMessage(errorMessage)
                .expiresAt(expiresAt)
                .createdAt(LocalDateTime.now())
                .build();
    }

    public void markVerified(LocalDateTime verifiedAt) {
        this.verifiedAt = verifiedAt;
    }
}
