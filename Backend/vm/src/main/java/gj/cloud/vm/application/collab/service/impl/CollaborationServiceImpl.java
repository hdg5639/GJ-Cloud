package gj.cloud.vm.application.collab.service.impl;

import gj.cloud.vm.application.collab.dto.*;
import gj.cloud.vm.application.collab.service.CollaborationService;
import gj.cloud.vm.domain.collab.entity.CollaborationItemEntity;
import gj.cloud.vm.domain.collab.entity.CollaborationTagEntity;
import gj.cloud.vm.domain.collab.enums.CollaborationType;
import gj.cloud.vm.domain.collab.enums.ScopeType;
import gj.cloud.vm.domain.collab.repository.CollaborationItemRepository;
import gj.cloud.vm.domain.collab.repository.CollaborationTagRepository;
import gj.cloud.vm.domain.org.enums.MemberRole;
import gj.cloud.vm.domain.org.enums.MemberStatus;
import gj.cloud.vm.domain.org.repository.OrganizationMemberRepository;
import gj.cloud.vm.domain.org.repository.OrganizationVmRepository;
import gj.cloud.vm.domain.vm.repository.VmRepository;
import gj.cloud.vm.global.exception.VmException;
import gj.cloud.vm.global.exception.enums.VmErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CollaborationServiceImpl implements CollaborationService {

    private static final int MAX_PINNED = 5;

    private final CollaborationItemRepository itemRepository;
    private final CollaborationTagRepository tagRepository;
    private final VmRepository vmRepository;
    private final OrganizationMemberRepository memberRepository;
    private final OrganizationVmRepository orgVmRepository;

    @Override
    public Flux<CollaborationResponse> list(ScopeType scopeType, UUID scopeId, CollaborationType typeFilter,
                                             String userId, String email) {
        return checkScopeAccess(scopeType, scopeId, userId, email)
                .thenMany(typeFilter != null
                        ? itemRepository.findAllByScopeAndType(scopeType.name(), scopeId, typeFilter.name())
                        : itemRepository.findAllByScopeOrderByPinnedDesc(scopeType.name(), scopeId))
                .map(CollaborationResponse::from);
    }

    @Override
    public Mono<CollaborationResponse> get(UUID id, String userId, String email) {
        return itemRepository.findById(id)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.COLLABORATION_NOT_FOUND)))
                .flatMap(item -> checkScopeAccess(item.getScopeType(), item.getScopeId(), userId, email)
                        .thenReturn(CollaborationResponse.from(item)));
    }

    @Override
    public Mono<CollaborationResponse> create(CollaborationCreateRequest req, String userId, String email) {
        return checkScopeAccess(req.scopeType(), req.scopeId(), userId, email)
                .then(Mono.defer(() -> {
                    String normalizedTag = req.tag() != null ? req.tag().trim().toLowerCase() : null;
                    CollaborationItemEntity item = CollaborationItemEntity.create(
                            req.scopeType(), req.scopeId(), req.type(),
                            normalizedTag, req.title(), req.content(), userId, email);
                    return itemRepository.save(item)
                            .flatMap(saved -> upsertTag(req.scopeType(), req.scopeId(), normalizedTag, userId)
                                    .thenReturn(CollaborationResponse.from(saved)));
                }));
    }

    @Override
    public Mono<CollaborationResponse> update(UUID id, CollaborationUpdateRequest req, String userId, String email) {
        return itemRepository.findById(id)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.COLLABORATION_NOT_FOUND)))
                .flatMap(item -> checkWritePermission(item, userId, email)
                        .then(Mono.defer(() -> {
                            String normalizedTag = req.tag() != null ? req.tag().trim().toLowerCase() : null;
                            return itemRepository.save(item.withUpdated(normalizedTag, req.title(), req.content()))
                                    .flatMap(saved -> upsertTag(item.getScopeType(), item.getScopeId(), normalizedTag, userId)
                                            .thenReturn(CollaborationResponse.from(saved)));
                        })));
    }

    @Override
    public Mono<Void> delete(UUID id, String userId, String email) {
        return itemRepository.findById(id)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.COLLABORATION_NOT_FOUND)))
                .flatMap(item -> checkWritePermission(item, userId, email)
                        .then(itemRepository.delete(item)));
    }

    @Override
    public Mono<CollaborationResponse> pin(UUID id, String userId, String email) {
        return itemRepository.findById(id)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.COLLABORATION_NOT_FOUND)))
                .flatMap(item -> checkPinPermission(item.getScopeType(), item.getScopeId(), userId, email)
                        .then(Mono.defer(() -> {
                            boolean newPinned = !item.isPinned();
                            if (!newPinned) {
                                return itemRepository.save(item.withPinned(false)).map(CollaborationResponse::from);
                            }
                            return itemRepository.countPinnedByScope(item.getScopeType().name(), item.getScopeId())
                                    .flatMap(count -> {
                                        if (count >= MAX_PINNED) {
                                            return itemRepository.findPinnedByScope(item.getScopeType().name(), item.getScopeId())
                                                    .next()
                                                    .flatMap(oldest -> itemRepository.save(oldest.withPinned(false)));
                                        }
                                        return Mono.empty();
                                    })
                                    .then(itemRepository.save(item.withPinned(true)))
                                    .map(CollaborationResponse::from);
                        })));
    }

    @Override
    public Mono<CollaborationResponse> resolve(UUID id, String userId, String email) {
        return itemRepository.findById(id)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.COLLABORATION_NOT_FOUND)))
                .flatMap(item -> {
                    if (item.getType() != CollaborationType.REQUEST) {
                        return Mono.error(new VmException(VmErrorCode.COLLABORATION_NOT_REQUEST_TYPE));
                    }
                    return checkScopeAccess(item.getScopeType(), item.getScopeId(), userId, email)
                            .then(itemRepository.save(item.withResolved(userId, email)))
                            .map(CollaborationResponse::from);
                });
    }

    @Override
    public Flux<TagResponse> searchTags(ScopeType scopeType, UUID scopeId, String query) {
        if (query == null || query.isBlank()) {
            return tagRepository.findAllByScope(scopeType.name(), scopeId).map(TagResponse::from);
        }
        return tagRepository.searchByPrefix(scopeType.name(), scopeId, query.trim().toLowerCase())
                .map(TagResponse::from);
    }

    @Override
    public Flux<TagResponse> listTags(ScopeType scopeType, UUID scopeId, String userId, String email) {
        return checkScopeAccess(scopeType, scopeId, userId, email)
                .thenMany(tagRepository.findAllByScope(scopeType.name(), scopeId))
                .map(TagResponse::from);
    }

    @Override
    public Mono<Void> deleteTag(UUID tagId, String userId, String email) {
        return tagRepository.findById(tagId)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.TAG_NOT_FOUND)))
                .flatMap(tag -> checkPinPermission(tag.getScopeType(), tag.getScopeId(), userId, email)
                        .then(tagRepository.delete(tag)));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Mono<Void> checkScopeAccess(ScopeType scopeType, UUID scopeId, String userId, String email) {
        if (scopeType == ScopeType.INSTANCE) {
            return vmRepository.findById(scopeId)
                    .switchIfEmpty(Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND)))
                    .flatMap(vm -> {
                        if (vm.getUserId().equals(userId) && vm.getDeletedAt() == null) return Mono.empty();
                        return orgVmRepository.findAllByVmId(scopeId)
                                .flatMap(orgVm -> memberRepository.findAcceptedByOrgIdAndEmail(orgVm.getOrganizationId(), email))
                                .next()
                                .<Void>flatMap(m -> Mono.empty())
                                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.COLLABORATION_PERMISSION_DENIED)));
                    });
        } else {
            return memberRepository.findAcceptedByOrgIdAndEmail(scopeId, email)
                    .switchIfEmpty(Mono.error(new VmException(VmErrorCode.NOT_ORGANIZATION_MEMBER)))
                    .then();
        }
    }

    private Mono<Void> checkWritePermission(CollaborationItemEntity item, String userId, String email) {
        if (item.getCreatedById().equals(userId)) return Mono.empty();
        return checkPinPermission(item.getScopeType(), item.getScopeId(), userId, email);
    }

    private Mono<Void> checkPinPermission(ScopeType scopeType, UUID scopeId, String userId, String email) {
        if (scopeType == ScopeType.INSTANCE) {
            return vmRepository.findById(scopeId)
                    .switchIfEmpty(Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND)))
                    .flatMap(vm -> {
                        if (vm.getUserId().equals(userId)) return Mono.empty();
                        return orgVmRepository.findAllByVmId(scopeId)
                                .flatMap(orgVm -> memberRepository.findAcceptedByOrgIdAndEmail(orgVm.getOrganizationId(), email))
                                .filter(m -> m.getRole() == MemberRole.OWNER || m.getRole() == MemberRole.ADMIN)
                                .next()
                                .<Void>flatMap(m -> Mono.empty())
                                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.COLLABORATION_PERMISSION_DENIED)));
                    });
        } else {
            return memberRepository.findAcceptedByOrgIdAndEmail(scopeId, email)
                    .switchIfEmpty(Mono.error(new VmException(VmErrorCode.NOT_ORGANIZATION_MEMBER)))
                    .flatMap(m -> {
                        if (m.getRole() == MemberRole.OWNER || m.getRole() == MemberRole.ADMIN) {
                            return Mono.<Void>empty();
                        }
                        return Mono.error(new VmException(VmErrorCode.COLLABORATION_PERMISSION_DENIED));
                    });
        }
    }

    private Mono<Void> upsertTag(ScopeType scopeType, UUID scopeId, String tag, String userId) {
        if (tag == null || tag.isBlank()) return Mono.empty();
        return tagRepository.findByScopeAndName(scopeType.name(), scopeId, tag)
                .flatMap(existing -> tagRepository.save(existing.withIncrementedUsage()))
                .switchIfEmpty(Mono.defer(() ->
                        tagRepository.save(CollaborationTagEntity.create(scopeType, scopeId, tag, userId))))
                .then();
    }
}
