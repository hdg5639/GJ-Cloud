package gj.cloud.vm.application.org.service.impl;

import gj.cloud.vm.application.org.dto.*;
import gj.cloud.vm.application.org.service.OrganizationService;
import gj.cloud.vm.application.ssh.client.UserServiceClient;
import gj.cloud.vm.application.vm.dto.VmResponse;
import gj.cloud.vm.domain.org.entity.OrganizationEntity;
import gj.cloud.vm.domain.org.entity.OrganizationMemberEntity;
import gj.cloud.vm.domain.org.entity.OrganizationVmEntity;
import gj.cloud.vm.domain.org.enums.MemberRole;
import gj.cloud.vm.domain.org.enums.MemberStatus;
import gj.cloud.vm.domain.org.repository.OrganizationMemberRepository;
import gj.cloud.vm.domain.org.repository.OrganizationRepository;
import gj.cloud.vm.domain.org.repository.OrganizationVmRepository;
import gj.cloud.vm.domain.vm.repository.VmRepository;
import gj.cloud.vm.global.exception.VmException;
import gj.cloud.vm.global.exception.enums.VmErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationServiceImpl implements OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;
    private final OrganizationVmRepository orgVmRepository;
    private final VmRepository vmRepository;
    private final UserServiceClient userServiceClient;

    @Override
    public Mono<OrgDetailResponse> create(String userId, String email, String bearerToken, OrgCreateRequest request) {
        return resolveOwnProfile(bearerToken, email)
                .flatMap(ownProfile -> {
                    OrganizationEntity org = OrganizationEntity.create(request.name(), userId);
                    return organizationRepository.save(org)
                            .flatMap(saved -> {
                                OrganizationMemberEntity ownerMember = OrganizationMemberEntity.createOwner(
                                        saved.getId(), userId, email, ownProfile.nickname(), ownProfile.profileImageUrl());
                                Mono<Void> ownerSave = memberRepository.save(ownerMember).then();

                                Mono<Void> inviteSave = Flux.fromIterable(
                                        request.invites() == null ? List.of() : request.invites()
                                ).flatMap(invite -> {
                                    MemberRole role = invite.role() != null ? invite.role() : MemberRole.MEMBER;
                                    return memberRepository.save(OrganizationMemberEntity.createInvite(saved.getId(), invite.email(), role));
                                }).then();

                                Mono<Void> vmsSave = Flux.fromIterable(
                                        request.vmIds() == null ? List.of() : request.vmIds()
                                ).flatMap(vmId -> orgVmRepository.save(OrganizationVmEntity.create(saved.getId(), vmId))).then();

                                return Mono.when(ownerSave, inviteSave, vmsSave).thenReturn(saved);
                            });
                })
                .flatMap(saved -> buildDetail(saved, email, bearerToken));
    }

    @Override
    public Flux<OrgResponse> listMyOrganizations(String email) {
        return organizationRepository.findAllByMemberEmail(email)
                .flatMap(org -> buildSummary(org, email));
    }

    @Override
    public Flux<OrgResponse> listPendingInvitations(String email) {
        return organizationRepository.findAllPendingByEmail(email)
                .flatMap(org -> memberRepository.findPendingByOrgIdAndEmail(org.getId(), email)
                        .map(m -> OrgResponse.ofInvitation(org, m.getRole(), m.getId())));
    }

    @Override
    public Mono<OrgDetailResponse> getDetail(UUID orgId, String email, String bearerToken) {
        return organizationRepository.findById(orgId)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.ORGANIZATION_NOT_FOUND)))
                .flatMap(org -> requireMember(orgId, email)
                        .then(buildDetail(org, email, bearerToken)));
    }

    @Override
    public Mono<OrgDetailResponse> update(UUID orgId, String email, OrgUpdateRequest request) {
        return organizationRepository.findById(orgId)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.ORGANIZATION_NOT_FOUND)))
                .flatMap(org -> requireRole(orgId, email, MemberRole.OWNER)
                        .flatMap(ignored -> organizationRepository.save(org.withName(request.name())))
                        .flatMap(saved -> buildDetail(saved, email, null)));
    }

    @Override
    public Mono<Void> delete(UUID orgId, String email) {
        return organizationRepository.findById(orgId)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.ORGANIZATION_NOT_FOUND)))
                .flatMap(org -> requireRole(orgId, email, MemberRole.OWNER))
                .then(memberRepository.deleteAllByOrganizationId(orgId))
                .then(organizationRepository.deleteById(orgId));
    }

    @Override
    public Mono<MemberResponse> invite(UUID orgId, String email, MemberInviteRequest request) {
        return organizationRepository.findById(orgId)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.ORGANIZATION_NOT_FOUND)))
                .flatMap(org -> requireRoleAtLeast(orgId, email, MemberRole.ADMIN))
                .then(memberRepository.findByOrganizationIdAndEmail(orgId, request.email())
                        .flatMap(existing -> Mono.<OrganizationMemberEntity>error(new VmException(VmErrorCode.MEMBER_ALREADY_EXISTS)))
                        .switchIfEmpty(Mono.defer(() -> {
                            MemberRole role = request.role() != null ? request.role() : MemberRole.MEMBER;
                            return memberRepository.save(OrganizationMemberEntity.createInvite(
                                    orgId, request.userId(), request.email(), request.nickname(), request.profileImageUrl(), role));
                        })))
                .map(MemberResponse::from);
    }

    @Override
    public Mono<List<MemberSearchResult>> searchMembers(UUID orgId, String email, String bearerToken, String query) {
        return organizationRepository.findById(orgId)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.ORGANIZATION_NOT_FOUND)))
                .flatMap(org -> requireRoleAtLeast(orgId, email, MemberRole.ADMIN))
                .then(userServiceClient.searchUsers(bearerToken, query));
    }

    @Override
    public Mono<MemberResponse> respond(UUID orgId, UUID memberId, String userId, String email, MemberRespondRequest request) {
        return memberRepository.findById(memberId)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.MEMBER_NOT_FOUND)))
                .flatMap(member -> {
                    if (!member.getEmail().equals(email)) {
                        return Mono.error(new VmException(VmErrorCode.ORGANIZATION_PERMISSION_DENIED));
                    }
                    if (member.getStatus() != MemberStatus.PENDING) {
                        return Mono.error(new VmException(VmErrorCode.INVITATION_NOT_PENDING));
                    }
                    OrganizationMemberEntity updated = request.accept()
                            ? member.withAccepted(userId)
                            : member.withRejected();
                    return memberRepository.save(updated);
                })
                .map(MemberResponse::from);
    }

    @Override
    public Mono<Void> removeMember(UUID orgId, UUID memberId, String email) {
        return memberRepository.findById(memberId)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.MEMBER_NOT_FOUND)))
                .flatMap(target -> {
                    if (target.getRole() == MemberRole.OWNER) {
                        return Mono.error(new VmException(VmErrorCode.CANNOT_REMOVE_OWNER));
                    }
                    if (target.getEmail().equals(email)) {
                        return memberRepository.delete(target);
                    }
                    return requireRoleAtLeast(orgId, email, MemberRole.ADMIN)
                            .then(memberRepository.delete(target));
                });
    }

    @Override
    public Mono<MemberResponse> updateMemberRole(UUID orgId, UUID memberId, String email, MemberRoleUpdateRequest request) {
        return requireRole(orgId, email, MemberRole.OWNER)
                .then(memberRepository.findById(memberId))
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.MEMBER_NOT_FOUND)))
                .flatMap(member -> {
                    if (member.getRole() == MemberRole.OWNER) {
                        return Mono.error(new VmException(VmErrorCode.CANNOT_REMOVE_OWNER));
                    }
                    return memberRepository.save(member.withRole(request.role()));
                })
                .map(MemberResponse::from);
    }

    @Override
    public Mono<Void> addVm(UUID orgId, String email, UUID vmId) {
        return requireRole(orgId, email, MemberRole.OWNER)
                .then(orgVmRepository.countByOrganizationIdAndVmId(orgId, vmId))
                .flatMap(count -> {
                    if (count > 0) return Mono.error(new VmException(VmErrorCode.VM_ALREADY_IN_ORGANIZATION));
                    return vmRepository.findById(vmId)
                            .switchIfEmpty(Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND)));
                })
                .flatMap(vm -> orgVmRepository.save(OrganizationVmEntity.create(orgId, vmId)))
                .then();
    }

    @Override
    public Mono<Void> removeVm(UUID orgId, String email, UUID vmId) {
        return requireRole(orgId, email, MemberRole.OWNER)
                .then(orgVmRepository.deleteByOrganizationIdAndVmId(orgId, vmId));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Mono<OrganizationMemberEntity> requireMember(UUID orgId, String email) {
        return memberRepository.findAcceptedByOrgIdAndEmail(orgId, email)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.NOT_ORGANIZATION_MEMBER)));
    }

    private Mono<OrganizationMemberEntity> requireRole(UUID orgId, String email, MemberRole required) {
        return requireMember(orgId, email)
                .flatMap(member -> {
                    if (member.getRole() != required) {
                        return Mono.error(new VmException(VmErrorCode.ORGANIZATION_PERMISSION_DENIED));
                    }
                    return Mono.just(member);
                });
    }

    private Mono<OrganizationMemberEntity> requireRoleAtLeast(UUID orgId, String email, MemberRole minimum) {
        return requireMember(orgId, email)
                .flatMap(member -> {
                    boolean ok = switch (minimum) {
                        case OWNER -> member.getRole() == MemberRole.OWNER;
                        case ADMIN -> member.getRole() == MemberRole.OWNER || member.getRole() == MemberRole.ADMIN;
                        case MEMBER -> true;
                    };
                    if (!ok) return Mono.error(new VmException(VmErrorCode.ORGANIZATION_PERMISSION_DENIED));
                    return Mono.just(member);
                });
    }

    private Mono<OrgResponse> buildSummary(OrganizationEntity org, String email) {
        return Mono.zip(
                memberRepository.findAcceptedByOrgIdAndEmail(org.getId(), email).map(OrganizationMemberEntity::getRole),
                memberRepository.countAcceptedByOrganizationId(org.getId()).map(Long::intValue),
                orgVmRepository.findAllByOrganizationId(org.getId()).count().map(Long::intValue)
        ).map(t -> OrgResponse.of(org, t.getT1(), t.getT2(), t.getT3()));
    }

    private Mono<OrgDetailResponse> buildDetail(OrganizationEntity org, String email, String bearerToken) {
        Mono<MemberRole> roleMono = memberRepository.findAcceptedByOrgIdAndEmail(org.getId(), email)
                .map(OrganizationMemberEntity::getRole)
                .defaultIfEmpty(MemberRole.MEMBER);

        Mono<List<MemberResponse>> membersMono = memberRepository.findAllByOrganizationId(org.getId())
                .flatMap(member -> withBackfilledSnapshot(member, bearerToken))
                .map(MemberResponse::from)
                .collectList();

        Mono<List<VmResponse>> vmsMono = orgVmRepository.findAllByOrganizationId(org.getId())
                .flatMap(orgVm -> vmRepository.findById(orgVm.getVmId()))
                .filter(vm -> vm.getDeletedAt() == null)
                .map(VmResponse::from)
                .collectList();

        return Mono.zip(roleMono, membersMono, vmsMono)
                .map(t -> OrgDetailResponse.of(org, t.getT1(), t.getT2(), t.getT3()));
    }

    // 닉네임 검색 기능 이전에 초대됐거나 이메일만으로 가입 전 초대돼 스냅샷이 없던 기존 멤버(오너 포함)를
    // 조회 시점에 채워 넣는다 — 한 번 채워지면 저장돼서 다음 조회부터는 다시 조회하지 않는다.
    private Mono<OrganizationMemberEntity> withBackfilledSnapshot(OrganizationMemberEntity member, String bearerToken) {
        if (member.getNickname() != null || bearerToken == null) {
            return Mono.just(member);
        }
        return userServiceClient.searchUsers(bearerToken, member.getEmail())
                .flatMapMany(Flux::fromIterable)
                .filter(r -> r.email().equalsIgnoreCase(member.getEmail()))
                .next()
                .flatMap(match -> memberRepository.save(
                        member.withProfileSnapshot(match.nickname(), match.profileImageUrl())))
                .defaultIfEmpty(member)
                .onErrorReturn(member);
    }

    // 조직 생성 시점의 오너 본인 프로필 스냅샷 조회 — 검색 API를 자기 자신의 이메일로 정확히 일치시켜 재사용.
    private Mono<MemberSearchResult> resolveOwnProfile(String bearerToken, String email) {
        if (bearerToken == null) {
            return Mono.just(new MemberSearchResult(null, null, email, null));
        }
        return userServiceClient.searchUsers(bearerToken, email)
                .flatMapMany(Flux::fromIterable)
                .filter(r -> r.email().equalsIgnoreCase(email))
                .next()
                .defaultIfEmpty(new MemberSearchResult(null, null, email, null))
                .onErrorReturn(new MemberSearchResult(null, null, email, null));
    }
}
