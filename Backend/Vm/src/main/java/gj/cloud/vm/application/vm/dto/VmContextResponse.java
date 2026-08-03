package gj.cloud.vm.application.vm.dto;

import gj.cloud.vm.domain.vm.entity.VmEntity;
import gj.cloud.vm.domain.vm.enums.VmPermission;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public record VmContextResponse(
        String vmId,
        String ownerId,
        String internalIp,
        String status,
        String role,
        List<String> permissions
) {
    public static VmContextResponse from(VmEntity vm, VmAccessContext access) {
        return new VmContextResponse(
                vm.getId().toString(),
                vm.getUserId(),
                vm.getInternalIp(),
                vm.getStatus().name(),
                access.role().name(),
                toNames(access.permissions())
        );
    }

    private static List<String> toNames(Set<VmPermission> permissions) {
        return permissions.stream().map(Enum::name).collect(Collectors.toList());
    }
}
