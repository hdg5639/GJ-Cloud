package gj.cloud.vm.domain.port.repository;

import gj.cloud.vm.domain.port.entity.VmPortAccessEmailEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface VmPortAccessEmailRepository extends ReactiveCrudRepository<VmPortAccessEmailEntity, UUID> {

    Flux<VmPortAccessEmailEntity> findAllByVmPortId(UUID vmPortId);

    Mono<Long> countByVmPortId(UUID vmPortId);

    Mono<VmPortAccessEmailEntity> findByVmPortIdAndEmail(UUID vmPortId, String email);

    Mono<Void> deleteAllByVmPortId(UUID vmPortId);
}
