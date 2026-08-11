package gj.cloud.vm.application.vm.dto;

import java.util.Set;
import java.util.UUID;

public record VmExistenceResponse(Set<UUID> existingVmIds) {
}
