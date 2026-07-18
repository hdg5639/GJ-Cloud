package gj.cloud.ops.domain.backup.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

// 11절 수동 DB 백업 이력 — 덤프 파일 자체는 DB가 아니라 VM 파일시스템(backups/ 디렉토리)에 저장되고,
// 여기는 메타데이터만 기록. 다운로드는 기존 파일 브라우저(FILE_READ 권한, /home/{sshUser} 이하)를 그대로 재사용함.
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

    @Column(nullable = false)
    private boolean succeeded;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static DbBackupEntity create(String vmId, String serviceName, String dbType, String filePath,
                                         Long fileSizeBytes, boolean succeeded, String errorMessage) {
        return DbBackupEntity.builder()
                .id(UUID.randomUUID().toString())
                .vmId(vmId)
                .serviceName(serviceName)
                .dbType(dbType)
                .filePath(filePath)
                .fileSizeBytes(fileSizeBytes)
                .succeeded(succeeded)
                .errorMessage(errorMessage)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
