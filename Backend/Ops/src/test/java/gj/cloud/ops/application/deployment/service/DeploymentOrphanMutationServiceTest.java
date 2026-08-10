package gj.cloud.ops.application.deployment.service;

import gj.cloud.ops.domain.deployment.entity.DeploymentOrphanCleanupEntity;
import gj.cloud.ops.domain.deployment.entity.DeploymentTargetEntity;
import gj.cloud.ops.domain.deployment.enums.SourceType;
import gj.cloud.ops.domain.deployment.repository.DeploymentOrphanCleanupRepository;
import gj.cloud.ops.domain.deployment.repository.DeploymentRepository;
import gj.cloud.ops.domain.deployment.repository.DeploymentTargetRepository;
import gj.cloud.ops.domain.preview.repository.ManagedPreviewDeploymentRepository;
import gj.cloud.ops.domain.preview.repository.RegressionSuiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeploymentOrphanMutationServiceTest {
    @Mock DeploymentTargetRepository targetRepository;
    @Mock DeploymentRepository deploymentRepository;
    @Mock DeploymentOrphanCleanupRepository cleanupRepository;
    @Mock ManagedPreviewDeploymentRepository managedPreviewRepository;
    @Mock RegressionSuiteRepository regressionSuiteRepository;
    @Mock DeploymentLockService lockService;
    @Mock StringRedisTemplate redisTemplate;

    private DeploymentOrphanMutationService service;

    @BeforeEach
    void setUp() {
        service = new DeploymentOrphanMutationService(
                targetRepository, deploymentRepository, cleanupRepository, managedPreviewRepository,
                regressionSuiteRepository, lockService, redisTemplate);
    }

    @Test
    void hardDeletesEmptyUnlockedTargetAndKeepsAuditEvent() {
        DeploymentTargetEntity target = target();
        when(targetRepository.findByIdForUpdate(target.getId())).thenReturn(Optional.of(target));
        when(deploymentRepository.countByDeploymentTargetId(target.getId())).thenReturn(0L);
        when(lockService.currentHolder(target.getId())).thenReturn(Optional.empty());

        var action = service.handleMissingVm(target.getId(), "VM_NOT_FOUND");

        assertThat(action).isEqualTo(DeploymentOrphanMutationService.CleanupAction.HARD_DELETED);
        verify(targetRepository).delete(target);
        verify(targetRepository, never()).quarantineOrphan(anyString(), anyString(), any());
        verify(cleanupRepository).save(argThat(event ->
                event.getAction().equals("HARD_DELETED") && !event.isHadRelatedData()));
        verify(redisTemplate).delete("auto-deploy-pending:" + target.getId());
    }

    @Test
    void quarantinesTargetWhenDeploymentHistoryExists() {
        DeploymentTargetEntity target = target();
        when(targetRepository.findByIdForUpdate(target.getId())).thenReturn(Optional.of(target));
        when(deploymentRepository.countByDeploymentTargetId(target.getId())).thenReturn(2L);
        when(lockService.currentHolder(target.getId())).thenReturn(Optional.empty());

        var action = service.handleMissingVm(target.getId(), "VM_NOT_FOUND");

        assertThat(action).isEqualTo(DeploymentOrphanMutationService.CleanupAction.QUARANTINED);
        verify(targetRepository).quarantineOrphan(eq(target.getId()), eq("VM_NOT_FOUND"), any(LocalDateTime.class));
        verify(targetRepository, never()).delete(any());
        verify(cleanupRepository).save(argThat(DeploymentOrphanCleanupEntity::isHadRelatedData));
        verify(redisTemplate).delete("auto-deploy-pending:" + target.getId());
    }

    @Test
    void quarantinesTargetWhenDeploymentLockStillExists() {
        DeploymentTargetEntity target = target();
        when(targetRepository.findByIdForUpdate(target.getId())).thenReturn(Optional.of(target));
        when(deploymentRepository.countByDeploymentTargetId(target.getId())).thenReturn(0L);
        when(lockService.currentHolder(target.getId())).thenReturn(Optional.of("deployment-1"));

        var action = service.handleMissingVm(target.getId(), "VM_NOT_FOUND");

        assertThat(action).isEqualTo(DeploymentOrphanMutationService.CleanupAction.QUARANTINED);
        verify(targetRepository).quarantineOrphan(eq(target.getId()), eq("VM_NOT_FOUND"), any(LocalDateTime.class));
        verify(targetRepository, never()).delete(any());
    }

    @Test
    void quarantinesTargetWhenPreviewOrRegressionDataStillReferencesIt() {
        DeploymentTargetEntity target = target();
        when(targetRepository.findByIdForUpdate(target.getId())).thenReturn(Optional.of(target));
        when(deploymentRepository.countByDeploymentTargetId(target.getId())).thenReturn(0L);
        when(managedPreviewRepository.existsByDeploymentTargetId(target.getId())).thenReturn(true);
        when(regressionSuiteRepository.existsByDeploymentTargetId(target.getId())).thenReturn(true);
        when(lockService.currentHolder(target.getId())).thenReturn(Optional.empty());

        var action = service.handleMissingVm(target.getId(), "VM_NOT_FOUND");

        assertThat(action).isEqualTo(DeploymentOrphanMutationService.CleanupAction.QUARANTINED);
        verify(targetRepository).quarantineOrphan(eq(target.getId()), eq("VM_NOT_FOUND"), any(LocalDateTime.class));
        verify(targetRepository, never()).delete(any());
        verify(cleanupRepository).save(argThat(DeploymentOrphanCleanupEntity::isHadRelatedData));
    }

    private DeploymentTargetEntity target() {
        LocalDateTime now = LocalDateTime.now();
        return DeploymentTargetEntity.builder()
                .id("target-1")
                .vmId("vm-missing")
                .ownerUserId("user-1")
                .ownerEmail("owner@example.com")
                .name("orphan-app")
                .repositoryUrl("https://github.com/example/repo.git")
                .branch("main")
                .sourceType(SourceType.RAW_COMPOSE)
                .sourceComposeCiphertext("ciphertext")
                .active(true)
                .autoDeployEnabled(true)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
