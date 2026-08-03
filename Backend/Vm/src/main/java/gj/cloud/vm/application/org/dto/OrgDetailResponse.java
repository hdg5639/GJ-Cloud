package gj.cloud.vm.application.org.dto;

import gj.cloud.vm.application.vm.dto.VmResponse;
import gj.cloud.vm.domain.org.entity.OrganizationEntity;
import gj.cloud.vm.domain.org.enums.MemberRole;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record OrgDetailResponse(
        UUID id,
        String name,
        String ownerId,
        MemberRole myRole,
        List<MemberResponse> members,
        List<VmResponse> vms,
        LocalDateTime createdAt
) {
    public static OrgDetailResponse of(OrganizationEntity org, MemberRole myRole,
                                        List<MemberResponse> members, List<VmResponse> vms) {
        return new OrgDetailResponse(org.getId(), org.getName(), org.getOwnerId(),
                myRole, members, vms, org.getCreatedAt());
    }
}
