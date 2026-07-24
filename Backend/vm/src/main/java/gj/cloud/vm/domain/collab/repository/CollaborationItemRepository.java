package gj.cloud.vm.domain.collab.repository;

import gj.cloud.vm.domain.collab.entity.CollaborationItemEntity;
import gj.cloud.vm.domain.collab.enums.ScopeType;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface CollaborationItemRepository extends ReactiveCrudRepository<CollaborationItemEntity, UUID> {

    @Query("SELECT * FROM collaboration_items WHERE scope_type = :scopeType AND scope_id = :scopeId " +
           "ORDER BY pinned DESC, created_at DESC")
    Flux<CollaborationItemEntity> findAllByScopeOrderByPinnedDesc(String scopeType, UUID scopeId);

    @Query("SELECT * FROM collaboration_items WHERE scope_type = :scopeType AND scope_id = :scopeId AND type = :type " +
           "ORDER BY pinned DESC, created_at DESC")
    Flux<CollaborationItemEntity> findAllByScopeAndType(String scopeType, UUID scopeId, String type);

    @Query("SELECT * FROM collaboration_items WHERE scope_type = :scopeType AND scope_id = :scopeId AND pinned = true " +
           "ORDER BY created_at ASC")
    Flux<CollaborationItemEntity> findPinnedByScope(String scopeType, UUID scopeId);

    @Query("SELECT COUNT(*) FROM collaboration_items WHERE scope_type = :scopeType AND scope_id = :scopeId AND pinned = true")
    Mono<Long> countPinnedByScope(String scopeType, UUID scopeId);

    @Query("UPDATE collaboration_items SET created_by_email = '탈퇴한 사용자' WHERE created_by_id = :userId")
    Mono<Void> anonymizeByUserId(String userId);

    @Query("DELETE FROM collaboration_items WHERE scope_type = :scopeType AND scope_id = :scopeId")
    Mono<Void> deleteAllByScope(String scopeType, UUID scopeId);
}
