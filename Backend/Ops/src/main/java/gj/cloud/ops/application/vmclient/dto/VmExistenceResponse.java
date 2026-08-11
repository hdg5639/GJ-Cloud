package gj.cloud.ops.application.vmclient.dto;

import java.util.Set;

public record VmExistenceResponse(Set<String> existingVmIds) {
}
