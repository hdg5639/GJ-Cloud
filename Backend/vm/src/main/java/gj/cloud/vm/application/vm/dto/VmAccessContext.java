package gj.cloud.vm.application.vm.dto;

import gj.cloud.vm.domain.org.enums.MemberRole;
import gj.cloud.vm.domain.vm.enums.VmPermission;

import java.util.Set;

public record VmAccessContext(MemberRole role, Set<VmPermission> permissions) {
}
