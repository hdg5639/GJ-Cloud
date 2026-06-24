package gj.cloud.vm.domain.collab.repository;

import gj.cloud.vm.domain.collab.entity.CollaborationTagEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface CollaborationTagRepository extends ReactiveCrudRepository<CollaborationTagEntity, UUID> {

    @Query("SELECT * FROM collaboration_tags WHERE scope_type = :scopeType AND scope_id = :scopeId AND name ILIKE :query || '%' " +
           "ORDER BY usage_count DESC, last_used_at DESC LIMIT 8")
    Flux<CollaborationTagEntity> searchByPrefix(String scopeType, UUID scopeId, String query);

    @Query("SELECT * FROM collaboration_tags WHERE scope_type = :scopeType AND scope_id = :scopeId " +
           "ORDER BY usage_count DESC, last_used_at DESC")
    Flux<CollaborationTagEntity> findAllByScope(String scopeType, UUID scopeId);

    @Query("SELECT * FROM collaboration_tags WHERE scope_type = :scopeType AND scope_id = :scopeId AND name = :name")
    Mono<CollaborationTagEntity> findByScopeAndName(String scopeType, UUID scopeId, String name);
}
