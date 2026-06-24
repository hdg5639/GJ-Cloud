package gj.cloud.vm.domain.org.repository;

import gj.cloud.vm.domain.org.entity.OrganizationMemberEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface OrganizationMemberRepository extends ReactiveCrudRepository<OrganizationMemberEntity, UUID> {

    Flux<OrganizationMemberEntity> findAllByOrganizationId(UUID organizationId);

    Mono<OrganizationMemberEntity> findByOrganizationIdAndEmail(UUID organizationId, String email);

    @Query("SELECT * FROM organization_members WHERE organization_id = :orgId AND email = :email AND status = 'ACCEPTED'")
    Mono<OrganizationMemberEntity> findAcceptedByOrgIdAndEmail(UUID orgId, String email);

    @Query("SELECT * FROM organization_members WHERE organization_id = :orgId AND email = :email AND status = 'PENDING'")
    Mono<OrganizationMemberEntity> findPendingByOrgIdAndEmail(UUID orgId, String email);

    @Query("DELETE FROM organization_members WHERE organization_id = :orgId")
    Mono<Void> deleteAllByOrganizationId(UUID orgId);

    @Query("SELECT COUNT(*) FROM organization_members WHERE organization_id = :orgId AND status = 'ACCEPTED'")
    Mono<Long> countAcceptedByOrganizationId(UUID orgId);

    @Query("SELECT COUNT(*) FROM organization_members WHERE organization_id = :orgId AND pinned = true")
    Mono<Long> countByOrganizationId(UUID orgId);
}
