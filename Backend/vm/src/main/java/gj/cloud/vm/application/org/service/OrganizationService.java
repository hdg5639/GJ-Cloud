package gj.cloud.vm.application.org.service;

import gj.cloud.vm.application.org.dto.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface OrganizationService {

    Mono<OrgDetailResponse> create(String userId, String email, OrgCreateRequest request);

    Flux<OrgResponse> listMyOrganizations(String email);

    Flux<OrgResponse> listPendingInvitations(String email);

    Mono<OrgDetailResponse> getDetail(UUID orgId, String email);

    Mono<OrgDetailResponse> update(UUID orgId, String email, OrgUpdateRequest request);

    Mono<Void> delete(UUID orgId, String email);

    Mono<MemberResponse> invite(UUID orgId, String email, MemberInviteRequest request);

    Mono<List<MemberSearchResult>> searchMembers(UUID orgId, String email, String bearerToken, String query);

    Mono<MemberResponse> respond(UUID orgId, UUID memberId, String userId, String email, MemberRespondRequest request);

    Mono<Void> removeMember(UUID orgId, UUID memberId, String email);

    Mono<MemberResponse> updateMemberRole(UUID orgId, UUID memberId, String email, MemberRoleUpdateRequest request);

    Mono<Void> addVm(UUID orgId, String email, UUID vmId);

    Mono<Void> removeVm(UUID orgId, String email, UUID vmId);
}
