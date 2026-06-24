package gj.cloud.vm.domain.org.entity;

import gj.cloud.vm.domain.org.enums.MemberRole;
import gj.cloud.vm.domain.org.enums.MemberStatus;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Table("organization_members")
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrganizationMemberEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    @Builder.Default
    private boolean isNew = false;

    @Override
    public boolean isNew() { return isNew; }

    @Column("organization_id")
    private UUID organizationId;

    private String email;

    @Column("user_id")
    private String userId;

    private MemberRole role;
    private MemberStatus status;

    @Column("invited_at")
    private LocalDateTime invitedAt;

    @Column("joined_at")
    private LocalDateTime joinedAt;

    public static OrganizationMemberEntity createOwner(UUID orgId, String ownerId, String ownerEmail) {
        return OrganizationMemberEntity.builder()
                .id(UUID.randomUUID())
                .isNew(true)
                .organizationId(orgId)
                .email(ownerEmail)
                .userId(ownerId)
                .role(MemberRole.OWNER)
                .status(MemberStatus.ACCEPTED)
                .invitedAt(LocalDateTime.now())
                .joinedAt(LocalDateTime.now())
                .build();
    }

    public static OrganizationMemberEntity createInvite(UUID orgId, String email, MemberRole role) {
        return OrganizationMemberEntity.builder()
                .id(UUID.randomUUID())
                .isNew(true)
                .organizationId(orgId)
                .email(email)
                .role(role)
                .status(MemberStatus.PENDING)
                .invitedAt(LocalDateTime.now())
                .build();
    }

    public OrganizationMemberEntity withAccepted(String userId) {
        return this.toBuilder()
                .isNew(false)
                .userId(userId)
                .status(MemberStatus.ACCEPTED)
                .joinedAt(LocalDateTime.now())
                .build();
    }

    public OrganizationMemberEntity withRejected() {
        return this.toBuilder().isNew(false).status(MemberStatus.REJECTED).build();
    }

    public OrganizationMemberEntity withRole(MemberRole role) {
        return this.toBuilder().isNew(false).role(role).build();
    }
}
