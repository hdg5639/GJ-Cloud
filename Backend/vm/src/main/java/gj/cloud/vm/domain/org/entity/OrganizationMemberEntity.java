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

    // 초대 시점의 스냅샷(비정규화) — email처럼 매 조회마다 User 서비스를 다시 호출하지 않기 위함.
    // 미가입 이메일 직접 초대 경로에서는 검색 결과가 없어 둘 다 null.
    private String nickname;

    @Column("profile_image_url")
    private String profileImageUrl;

    private MemberRole role;
    private MemberStatus status;

    @Column("invited_at")
    private LocalDateTime invitedAt;

    @Column("joined_at")
    private LocalDateTime joinedAt;

    public static OrganizationMemberEntity createOwner(
            UUID orgId, String ownerId, String ownerEmail, String nickname, String profileImageUrl) {
        return OrganizationMemberEntity.builder()
                .id(UUID.randomUUID())
                .isNew(true)
                .organizationId(orgId)
                .email(ownerEmail)
                .userId(ownerId)
                .nickname(nickname)
                .profileImageUrl(profileImageUrl)
                .role(MemberRole.OWNER)
                .status(MemberStatus.ACCEPTED)
                .invitedAt(LocalDateTime.now())
                .joinedAt(LocalDateTime.now())
                .build();
    }

    public static OrganizationMemberEntity createInvite(UUID orgId, String email, MemberRole role) {
        return createInvite(orgId, null, email, null, null, role);
    }

    // 검색 결과에서 선택해 초대한 경우 — userId/nickname/profileImageUrl 스냅샷을 함께 저장.
    // userId가 이 시점에 채워져도 실제 접근 권한 판단(requireMember 등)은 항상 status='ACCEPTED' +
    // email 매칭만 보므로, 아직 수락 전인 PENDING 레코드에 userId가 있다고 접근 권한이 생기지 않는다.
    public static OrganizationMemberEntity createInvite(
            UUID orgId, String userId, String email, String nickname, String profileImageUrl, MemberRole role) {
        return OrganizationMemberEntity.builder()
                .id(UUID.randomUUID())
                .isNew(true)
                .organizationId(orgId)
                .userId(userId)
                .email(email)
                .nickname(nickname)
                .profileImageUrl(profileImageUrl)
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

    // 초대 당시 스냅샷이 없던(닉네임 검색 기능 이전에 초대됐거나, 이메일만으로 가입 전 초대돼 스냅샷을
    // 못 남겼던) 기존 멤버를 조회 시점에 채워 넣기 위함 — OrganizationServiceImpl에서 lazy backfill로 사용.
    public OrganizationMemberEntity withProfileSnapshot(String nickname, String profileImageUrl) {
        return this.toBuilder().isNew(false).nickname(nickname).profileImageUrl(profileImageUrl).build();
    }
}
