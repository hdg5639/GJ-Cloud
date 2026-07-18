package gj.cloud.ops.application.backup.dto;

import jakarta.validation.constraints.NotBlank;

// dbType: postgresql/mysql/redis/mongodb. username/password는 postgresql·mysql에서만 사용되며 저장하지 않음
// (요청 처리 중 단발성 SSH exec에만 사용하고 즉시 버림 — GIT_ASKPASS 패턴과 동일한 원칙)
public record DbBackupRequest(
        @NotBlank String serviceName,
        @NotBlank String dbType,
        @NotBlank String database,
        String username,
        String password
) {
}
