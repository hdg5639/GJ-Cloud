package gj.cloud.vm.application.collab.service;

import gj.cloud.vm.application.collab.dto.*;
import gj.cloud.vm.domain.collab.enums.CollaborationType;
import gj.cloud.vm.domain.collab.enums.ScopeType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface CollaborationService {

    Flux<CollaborationResponse> list(ScopeType scopeType, UUID scopeId, CollaborationType typeFilter,
                                     String userId, String email);

    Mono<CollaborationResponse> get(UUID id, String userId, String email);

    Mono<CollaborationResponse> create(CollaborationCreateRequest request, String userId, String email);

    Mono<CollaborationResponse> update(UUID id, CollaborationUpdateRequest request, String userId, String email);

    Mono<Void> delete(UUID id, String userId, String email);

    Mono<CollaborationResponse> pin(UUID id, String userId, String email);

    Mono<CollaborationResponse> resolve(UUID id, String userId, String email);

    Flux<TagResponse> searchTags(ScopeType scopeType, UUID scopeId, String query);

    Flux<TagResponse> listTags(ScopeType scopeType, UUID scopeId, String userId, String email);

    Mono<Void> deleteTag(UUID tagId, String userId, String email);
}
