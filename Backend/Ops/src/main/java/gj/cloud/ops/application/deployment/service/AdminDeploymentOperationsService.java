package gj.cloud.ops.application.deployment.service;

import gj.cloud.ops.application.deployment.dto.AdminDeploymentOperationsResponse;
import gj.cloud.ops.application.deployment.dto.DeploymentEventPayload;
import gj.cloud.ops.application.deployment.dto.OrphanReconcileResult;
import gj.cloud.ops.domain.deployment.entity.DeploymentEventEntity;
import gj.cloud.ops.domain.deployment.enums.DeploymentStatus;
import gj.cloud.ops.domain.deployment.repository.DeploymentEventRepository;
import gj.cloud.ops.domain.deployment.repository.DeploymentOrphanCleanupRepository;
import gj.cloud.ops.domain.deployment.repository.DeploymentRepository;
import gj.cloud.ops.domain.deployment.repository.DeploymentTargetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
public class AdminDeploymentOperationsService {
    private final DeploymentTargetRepository targetRepository;
    private final DeploymentRepository deploymentRepository;
    private final DeploymentEventRepository deploymentEventRepository;
    private final DeploymentOrphanCleanupRepository cleanupRepository;
    private final DeploymentOrphanReconciler orphanReconciler;

    @Transactional(readOnly = true)
    public AdminDeploymentOperationsResponse getOverview() {
        var targets = targetRepository.findTop200ByOrderByUpdatedAtDesc();
        var deployments = deploymentRepository.findTop100ByOrderByCreatedAtDesc();
        List<String> deploymentIds = deployments.stream().map(deployment -> deployment.getId()).toList();
        Map<String, String> latestEvents = new HashMap<>();
        if (!deploymentIds.isEmpty()) {
            for (DeploymentEventEntity event : deploymentEventRepository
                    .findLatestByDeploymentIdIn(deploymentIds)) {
                latestEvents.put(event.getDeploymentId(), event.getMessage());
            }
        }

        int recentFailed = (int) deployments.stream()
                .filter(deployment -> deployment.getStatus() == DeploymentStatus.FAILED).count();

        return new AdminDeploymentOperationsResponse(
                new AdminDeploymentOperationsResponse.Summary(
                        Math.toIntExact(targetRepository.count()),
                        Math.toIntExact(targetRepository.countByActiveTrue()),
                        Math.toIntExact(targetRepository.countByActiveTrueAndAutoDeployEnabledTrue()),
                        Math.toIntExact(targetRepository.countByOrphanedAtIsNotNull()),
                        recentFailed),
                targets.stream().map(AdminDeploymentOperationsResponse.Target::from).toList(),
                deployments.stream().map(deployment -> AdminDeploymentOperationsResponse.Deployment.from(
                        deployment, latestEvents.get(deployment.getId()))).toList(),
                cleanupRepository.findTop100ByOrderByCreatedAtDesc().stream()
                        .map(AdminDeploymentOperationsResponse.CleanupEvent::from).toList());
    }

    public OrphanReconcileResult reconcileOrphans() {
        return orphanReconciler.reconcileNow();
    }

    @Transactional(readOnly = true)
    public List<DeploymentEventPayload> getDeploymentEvents(String deploymentId) {
        return deploymentEventRepository.findTop1000ByDeploymentIdOrderBySequenceDesc(deploymentId).stream()
                .sorted(Comparator.comparingLong(DeploymentEventEntity::getSequence))
                .map(DeploymentEventPayload::from)
                .toList();
    }
}
