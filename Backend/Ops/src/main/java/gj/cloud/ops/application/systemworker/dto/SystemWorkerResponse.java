package gj.cloud.ops.application.systemworker.dto;

import gj.cloud.ops.domain.systemworker.entity.SystemWorkerEntity;

import java.time.LocalDateTime;

public record SystemWorkerResponse(
        boolean configured, String id, String role, String name, Integer vmId, String node, String internalIp,
        String status, String provisioningStage, int cores, int memoryMb, int diskGb,
        LocalDateTime lastHealthCheckAt, String lastError, LocalDateTime createdAt, LocalDateTime updatedAt
) {
    public static SystemWorkerResponse notConfigured() {
        return new SystemWorkerResponse(false, null, "AUTO_PREVIEW", "Auto Preview Worker", null, null, null,
                "NOT_CONFIGURED", null, 4, 5120, 80, null, null, null, null);
    }
    public static SystemWorkerResponse from(SystemWorkerEntity w) {
        return new SystemWorkerResponse(true, w.getId(), w.getRole().name(), w.getName(), w.getVmId(), w.getNode(),
                w.getInternalIp(), w.getStatus().name(), w.getProvisioningStage(), w.getCores(), w.getMemoryMb(),
                w.getDiskGb(), w.getLastHealthCheckAt(), w.getLastError(), w.getCreatedAt(), w.getUpdatedAt());
    }
}
