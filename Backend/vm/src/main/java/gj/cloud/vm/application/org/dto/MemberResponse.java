package gj.cloud.vm.application.org.dto;

import gj.cloud.vm.domain.org.entity.OrganizationMemberEntity;
import gj.cloud.vm.domain.org.enums.MemberRole;
import gj.cloud.vm.domain.org.enums.MemberStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record MemberResponse(
        UUID id,
        String email,
        String userId,
        MemberRole role,
        MemberStatus status,
        LocalDateTime invitedAt,
        LocalDateTime joinedAt
) {
    public static MemberResponse from(OrganizationMemberEntity e) {
        return new MemberResponse(e.getId(), e.getEmail(), e.getUserId(),
                e.getRole(), e.getStatus(), e.getInvitedAt(), e.getJoinedAt());
    }
}
