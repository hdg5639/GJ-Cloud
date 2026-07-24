package gj.cloud.vm.domain.port.repository;

import gj.cloud.vm.domain.port.entity.VmSshAccessEmailEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface VmSshAccessEmailRepository extends ReactiveCrudRepository<VmSshAccessEmailEntity, UUID> {

    Flux<VmSshAccessEmailEntity> findAllByVmId(UUID vmId);

    Mono<Long> countByVmId(UUID vmId);

    Mono<VmSshAccessEmailEntity> findByVmIdAndEmail(UUID vmId, String email);

    Mono<Void> deleteAllByVmId(UUID vmId);
}
