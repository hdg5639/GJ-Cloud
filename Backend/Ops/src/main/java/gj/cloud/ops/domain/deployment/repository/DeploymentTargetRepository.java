package gj.cloud.ops.domain.deployment.repository;

import gj.cloud.ops.domain.deployment.entity.DeploymentTargetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;

public interface DeploymentTargetRepository extends JpaRepository<DeploymentTargetEntity, String> {

    List<DeploymentTargetEntity> findAllByVmIdAndActiveTrueOrderByCreatedAtAsc(String vmId);

    @Query("""
            select new gj.cloud.ops.domain.deployment.repository.ActiveDeploymentTargetVmRef(target.id, target.vmId)
              from DeploymentTargetEntity target
             where target.active = true
             order by target.createdAt asc
            """)
    List<ActiveDeploymentTargetVmRef> findActiveTargetVmRefs();

    @Query("""
            select new gj.cloud.ops.domain.deployment.repository.PendingAutomaticTarget(
                   target.id, target.latestRequestedRevision)
              from DeploymentTargetEntity target
             where target.active = true
               and target.autoDeployEnabled = true
               and target.latestRequestedRevision is not null
               and (
                    target.latestRequestedAt is null
                    or not exists (
                        select deployment.id
                          from DeploymentEntity deployment
                         where deployment.deploymentTargetId = target.id
                           and deployment.requestedRevision = target.latestRequestedRevision
                           and deployment.createdAt >= target.latestRequestedAt
                    )
               )
             order by target.createdAt asc
            """)
    List<PendingAutomaticTarget> findPendingAutomaticTargets();

    @Query("""
            select target.id
              from DeploymentTargetEntity target
             where (target.active = false or target.autoDeployEnabled = false)
               and target.latestRequestedRevision is not null
            """)
    List<String> findDisabledTargetIdsWithRequestedRevision();

    @Query(value = """
            SELECT target.id AS targetId, target.latest_deployment_id AS deploymentId
              FROM deployment_targets target
              JOIN deployments deployment ON deployment.id = target.latest_deployment_id
             WHERE target.active = true
               AND deployment.status = 'STOPPED'
            """, nativeQuery = true)
    List<ActiveDeploymentPointerProjection> findStoppedActiveDeploymentPointers();

    List<DeploymentTargetEntity> findTop200ByOrderByUpdatedAtDesc();

    long countByActiveTrue();

    long countByActiveTrueAndAutoDeployEnabledTrue();

    long countByOrphanedAtIsNotNull();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select target from DeploymentTargetEntity target where target.id = :targetId")
    Optional<DeploymentTargetEntity> findByIdForUpdate(@Param("targetId") String targetId);

    Optional<DeploymentTargetEntity> findByIdAndVmIdAndActiveTrue(String id, String vmId);

    Optional<DeploymentTargetEntity> findByIdAndOwnerUserIdAndActiveTrue(String id, String ownerUserId);

    boolean existsByVmIdAndNameIgnoreCaseAndActiveTrue(String vmId, String name);

    List<DeploymentTargetEntity> findAllByGithubInstallationIdAndGithubRepositoryIdAndAutoDeployEnabledTrueAndActiveTrue(
            Long githubInstallationId, Long githubRepositoryId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update DeploymentTargetEntity target
               set target.latestRequestedRevision = :revision,
                   target.latestRequestedAt = :requestedAt,
                   target.updatedAt = :updatedAt
             where target.id = :targetId
            """)
    int markRequested(
            @Param("targetId") String targetId,
            @Param("revision") String revision,
            @Param("requestedAt") LocalDateTime requestedAt,
            @Param("updatedAt") LocalDateTime updatedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update DeploymentTargetEntity target
               set target.latestDeploymentId = :deploymentId,
                   target.latestDeployedRevision = :revision,
                   target.updatedAt = :updatedAt
             where target.id = :targetId
            """)
    int markDeploymentSucceeded(
            @Param("targetId") String targetId,
            @Param("deploymentId") String deploymentId,
            @Param("revision") String revision,
            @Param("updatedAt") LocalDateTime updatedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update DeploymentTargetEntity target
               set target.latestDeploymentId = null,
                   target.latestDeployedRevision = null,
                   target.updatedAt = :updatedAt
             where target.id = :targetId
               and target.latestDeploymentId = :deploymentId
            """)
    int clearActiveDeployment(
            @Param("targetId") String targetId,
            @Param("deploymentId") String deploymentId,
            @Param("updatedAt") LocalDateTime updatedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update DeploymentTargetEntity target
               set target.autoDeployEnabled = :enabled,
                   target.updatedAt = :updatedAt
             where target.id = :targetId
            """)
    int updateAutoDeploy(
            @Param("targetId") String targetId,
            @Param("enabled") boolean enabled,
            @Param("updatedAt") LocalDateTime updatedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update DeploymentTargetEntity target
               set target.active = false,
                   target.autoDeployEnabled = false,
                   target.updatedAt = :updatedAt
             where target.id = :targetId
            """)
    int deactivate(@Param("targetId") String targetId, @Param("updatedAt") LocalDateTime updatedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update DeploymentTargetEntity target
               set target.active = false,
                   target.autoDeployEnabled = false,
                   target.orphanedAt = :orphanedAt,
                   target.orphanReason = :reason,
                   target.updatedAt = :orphanedAt
             where target.id = :targetId
            """)
    int quarantineOrphan(
            @Param("targetId") String targetId,
            @Param("reason") String reason,
            @Param("orphanedAt") LocalDateTime orphanedAt);
}
