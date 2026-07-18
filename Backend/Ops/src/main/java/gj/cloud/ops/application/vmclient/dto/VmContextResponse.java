package gj.cloud.ops.application.vmclient.dto;

import java.util.List;

// VM 서비스 GET /internal/ops/vms/{vmId}/context 응답 미러링
public record VmContextResponse(
        String vmId,
        String ownerId,
        String internalIp,
        String status,
        String role,
        List<String> permissions
) {
    public boolean hasPermission(String permission) {
        return permissions != null && permissions.contains(permission);
    }
}
