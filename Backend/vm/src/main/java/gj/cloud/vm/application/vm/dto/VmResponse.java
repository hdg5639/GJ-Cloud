package gj.cloud.vm.application.vm.dto;

import gj.cloud.vm.domain.vm.entity.VmEntity;

import java.time.LocalDateTime;

public record VmResponse(
        String id,
        String name,
        String planType,
        String status,
        String internalIp,
        String subdomain,
        String errorMessage,
        LocalDateTime createdAt
) {
    public static VmResponse from(VmEntity entity) {
        return new VmResponse(
                entity.getId().toString(),
                entity.getName(),
                entity.getPlanType().name(),
                entity.getStatus().name(),
                entity.getInternalIp(),
                entity.getSubdomain(),
                entity.getErrorMessage(),
                entity.getCreatedAt()
        );
    }
}
