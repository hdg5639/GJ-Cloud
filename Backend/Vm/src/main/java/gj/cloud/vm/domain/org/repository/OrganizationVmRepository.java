package gj.cloud.vm.domain.org.repository;

import gj.cloud.vm.domain.org.entity.OrganizationVmEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface OrganizationVmRepository extends ReactiveCrudRepository<OrganizationVmEntity, UUID> {

    Flux<OrganizationVmEntity> findAllByOrganizationId(UUID organizationId);

    Flux<OrganizationVmEntity> findAllByVmId(UUID vmId);

    Mono<OrganizationVmEntity> findByOrganizationIdAndVmId(UUID organizationId, UUID vmId);

    @Query("DELETE FROM organization_vms WHERE organization_id = :orgId AND vm_id = :vmId")
    Mono<Void> deleteByOrganizationIdAndVmId(UUID orgId, UUID vmId);

    @Query("SELECT COUNT(*) FROM organization_vms WHERE organization_id = :orgId AND vm_id = :vmId")
    Mono<Long> countByOrganizationIdAndVmId(UUID orgId, UUID vmId);
}
