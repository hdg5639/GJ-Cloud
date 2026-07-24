package gj.cloud.vm.application.vm.dto;

import gj.cloud.vm.domain.vm.entity.VmEntity;

import java.time.LocalDateTime;

public record VmResponse(
        String id,
        String userId,
        String name,
        String planType,
        String status,
        String internalIp,
        String subdomain,
        int diskSizeGb,
        Boolean needsReboot,
        String errorMessage,
        LocalDateTime createdAt
) {
    public static VmResponse from(VmEntity entity) {
        return from(entity, null);
    }

    public static VmResponse from(VmEntity entity, Boolean needsReboot) {
        return new VmResponse(
                entity.getId().toString(),
                entity.getUserId(),
                entity.getName(),
                entity.getPlanType().name(),
                entity.getStatus().name(),
                entity.getInternalIp(),
                entity.getSubdomain(),
                entity.getDiskSizeGb(),
                needsReboot,
                entity.getErrorMessage(),
                entity.getCreatedAt()
        );
    }
}
