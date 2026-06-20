package gj.cloud.vm.domain.port.repository;

import gj.cloud.vm.domain.port.entity.VmPortEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface VmPortRepository extends ReactiveCrudRepository<VmPortEntity, UUID> {

    Flux<VmPortEntity> findAllByVmId(UUID vmId);

    @Query("SELECT COUNT(*) FROM vm_ports WHERE vm_id = :vmId")
    Mono<Long> countByVmId(UUID vmId);

    @Query("SELECT COUNT(*) FROM vm_ports WHERE vm_id = :vmId AND port = :port")
    Mono<Long> countByVmIdAndPort(UUID vmId, int port);
}
