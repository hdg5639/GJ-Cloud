package gj.cloud.vm.application.user.service.impl;

import gj.cloud.vm.application.user.service.UserCleanupService;
import gj.cloud.vm.application.vm.service.VmService;
import gj.cloud.vm.domain.collab.enums.ScopeType;
import gj.cloud.vm.domain.collab.repository.CollaborationItemRepository;
import gj.cloud.vm.domain.collab.repository.CollaborationTagRepository;
import gj.cloud.vm.domain.org.repository.OrganizationMemberRepository;
import gj.cloud.vm.domain.org.repository.OrganizationRepository;
import gj.cloud.vm.domain.org.repository.OrganizationVmRepository;
import gj.cloud.vm.domain.vm.repository.VmRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserCleanupServiceImpl implements UserCleanupService {

    private final VmService vmService;
    private final VmRepository vmRepository;
    private final OrganizationRepository orgRepository;
    private final OrganizationMemberRepository orgMemberRepository;
    private final OrganizationVmRepository orgVmRepository;
    private final CollaborationItemRepository collabItemRepository;
    private final CollaborationTagRepository collabTagRepository;

    @Override
    public Mono<Void> deleteUserData(String userId, String email) {
        return deleteOwnedVms(userId)
                .then(deleteOwnedOrgs(userId))
                .then(orgMemberRepository.deleteAllByEmail(email))
                .then(collabItemRepository.anonymizeByUserId(userId))
                .doOnSuccess(v -> log.info("사용자 데이터 정리 완료: userId={}", userId))
                .onErrorResume(e -> {
                    log.error("사용자 데이터 정리 실패: userId={}, error={}", userId, e.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<Void> deleteOwnedVms(String userId) {
        return vmRepository.findAllByUserIdAndNotDeleted(userId)
                .flatMap(vm -> vmService.deleteVmInternal(vm.getId())
                        .onErrorResume(e -> {
                            log.warn("VM 삭제 실패 (무시): vmId={}, error={}", vm.getId(), e.getMessage());
                            return Mono.empty();
                        }))
                .then();
    }

    private Mono<Void> deleteOwnedOrgs(String userId) {
        return orgRepository.findAllByOwnerId(userId)
                .flatMap(org -> {
                    Mono<Void> deleteCollabItems = collabItemRepository
                            .deleteAllByScope(ScopeType.ORGANIZATION.name(), org.getId());
                    Mono<Void> deleteCollabTags = collabTagRepository
                            .deleteAllByScope(ScopeType.ORGANIZATION.name(), org.getId());
                    Mono<Void> deleteOrgVms = orgVmRepository.findAllByOrganizationId(org.getId())
                            .flatMap(ov -> orgVmRepository.delete(ov))
                            .then();
                    Mono<Void> deleteMembers = orgMemberRepository.deleteAllByOrganizationId(org.getId());
                    Mono<Void> deleteOrg = orgRepository.delete(org);

                    return deleteCollabItems
                            .then(deleteCollabTags)
                            .then(deleteOrgVms)
                            .then(deleteMembers)
                            .then(deleteOrg);
                })
                .then();
    }
}
