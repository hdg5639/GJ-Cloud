package gj.cloud.vm.domain.vm.repository;

import gj.cloud.vm.domain.vm.entity.VmEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface VmRepository extends ReactiveCrudRepository<VmEntity, UUID> {

    @Query("SELECT * FROM vms WHERE user_id = :userId AND deleted_at IS NULL")
    Flux<VmEntity> findAllByUserIdAndNotDeleted(String userId);

    @Query("SELECT vmid FROM vms WHERE vmid IS NOT NULL AND deleted_at IS NULL")
    Flux<Integer> findAllActiveVmids();

    @Query("SELECT COUNT(*) FROM vms WHERE user_id = :userId AND deleted_at IS NULL AND status NOT IN ('PENDING', 'FAILED', 'DELETED')")
    Mono<Long> countActiveByUserId(String userId);

    @Query("SELECT internal_ip FROM vms WHERE status NOT IN ('DELETED', 'FAILED') AND internal_ip IS NOT NULL")
    Flux<String> findAllActiveInternalIps();
}
