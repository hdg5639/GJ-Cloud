package gj.cloud.ops.domain.deployment.repository;

import gj.cloud.ops.domain.deployment.entity.DeploymentTargetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DeploymentTargetRepository extends JpaRepository<DeploymentTargetEntity, String> {

    List<DeploymentTargetEntity> findAllByVmIdAndActiveTrueOrderByCreatedAtAsc(String vmId);

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
}
