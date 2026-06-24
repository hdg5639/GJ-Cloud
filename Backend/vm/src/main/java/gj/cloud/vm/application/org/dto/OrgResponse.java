package gj.cloud.vm.application.org.dto;

import gj.cloud.vm.domain.org.entity.OrganizationEntity;
import gj.cloud.vm.domain.org.enums.MemberRole;

import java.time.LocalDateTime;
import java.util.UUID;

public record OrgResponse(
        UUID id,
        String name,
        String ownerId,
        MemberRole myRole,
        int memberCount,
        int vmCount,
        LocalDateTime createdAt,
        UUID pendingMemberId
) {
    public static OrgResponse of(OrganizationEntity org, MemberRole myRole, int memberCount, int vmCount) {
        return new OrgResponse(org.getId(), org.getName(), org.getOwnerId(),
                myRole, memberCount, vmCount, org.getCreatedAt(), null);
    }

    public static OrgResponse ofInvitation(OrganizationEntity org, MemberRole myRole, UUID pendingMemberId) {
        return new OrgResponse(org.getId(), org.getName(), org.getOwnerId(),
                myRole, 0, 0, org.getCreatedAt(), pendingMemberId);
    }
}
