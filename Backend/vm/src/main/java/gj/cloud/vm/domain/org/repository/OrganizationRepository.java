package gj.cloud.vm.domain.org.repository;

import gj.cloud.vm.domain.org.entity.OrganizationEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface OrganizationRepository extends ReactiveCrudRepository<OrganizationEntity, UUID> {

    @Query("SELECT o.* FROM organizations o " +
           "JOIN organization_members m ON o.id = m.organization_id " +
           "WHERE m.email = :email AND m.status = 'ACCEPTED'")
    Flux<OrganizationEntity> findAllByMemberEmail(String email);

    @Query("SELECT o.* FROM organizations o " +
           "JOIN organization_members m ON o.id = m.organization_id " +
           "WHERE m.email = :email AND m.status = 'PENDING'")
    Flux<OrganizationEntity> findAllPendingByEmail(String email);
}
