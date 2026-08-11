package gj.cloud.ops.application.preview.managed;

import com.jcraft.jsch.Session;
import gj.cloud.ops.application.deployment.dto.ComposeArtifact;
import gj.cloud.ops.application.deployment.dto.RepoConfig;
import gj.cloud.ops.application.deployment.service.DeploymentExecutor;
import gj.cloud.ops.application.deployment.service.DeploymentTargetService;
import gj.cloud.ops.application.preview.managed.dto.ManagedPreviewResponse;
import gj.cloud.ops.application.systemworker.SystemWorkerService;
import gj.cloud.ops.application.systemworker.SystemWorkerVmClient;
import gj.cloud.ops.application.systemworker.dto.ManagedPreviewRouteResponse;
import gj.cloud.ops.application.userclient.UserPlanClient;
import gj.cloud.ops.domain.deployment.entity.DeploymentEntity;
import gj.cloud.ops.domain.deployment.entity.DeploymentTargetEntity;
import gj.cloud.ops.domain.deployment.enums.DeploymentStatus;
import gj.cloud.ops.domain.deployment.repository.DeploymentRepository;
import gj.cloud.ops.domain.preview.entity.ManagedPreviewDeploymentEntity;
import gj.cloud.ops.domain.preview.enums.ManagedPreviewStatus;
import gj.cloud.ops.domain.preview.repository.ManagedPreviewDeploymentRepository;
import gj.cloud.ops.domain.systemworker.entity.SystemWorkerEntity;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import gj.cloud.ops.global.ssh.SshCommandExecutor;
import gj.cloud.ops.global.ssh.VmSshSessionFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManagedPreviewService {
    private static final Set<ManagedPreviewStatus> OCCUPYING = EnumSet.of(ManagedPreviewStatus.ALLOCATED,
            ManagedPreviewStatus.QUEUED, ManagedPreviewStatus.BUILDING, ManagedPreviewStatus.RUNNING,
            ManagedPreviewStatus.FAILED);

    private final ManagedPreviewDeploymentRepository repository;
    private final DeploymentRepository deploymentRepository;
    private final SystemWorkerService systemWorkerService;
    private final SystemWorkerVmClient vmClient;
    private final UserPlanClient userPlanClient;
    private final ManagedPreviewProperties properties;
    private final DeploymentTargetService targetService;
    private final DeploymentExecutor deploymentExecutor;
    private final VmSshSessionFactory sessionFactory;
    private final SshCommandExecutor commands;

    @Transactional
    public synchronized ManagedPreviewDeploymentEntity allocate(String bearerToken, String userId) {
        SystemWorkerEntity worker = systemWorkerService.requireActive();
        int port = repository.findFirstAvailablePort(properties.getPortStart(), properties.getPortEnd())
                .orElseThrow(() -> new OpsException(OpsErrorCode.MANAGED_PREVIEW_CAPACITY_EXHAUSTED));
        int ttl = userPlanClient.isPro(bearerToken) ? properties.getProTtlHours() : properties.getFreeTtlHours();
        return repository.saveAndFlush(ManagedPreviewDeploymentEntity.allocate(userId, worker.getId(), port, ttl));
    }

    @Transactional
    public ManagedPreviewResponse deploy(ManagedPreviewDeploymentEntity preview, String ownerEmail,
                                         String targetName, ComposeArtifact artifact) {
        SystemWorkerEntity worker = systemWorkerService.requireActive();
        if (!worker.getId().equals(preview.getWorkerId())) throw new OpsException(OpsErrorCode.SYSTEM_WORKER_NOT_ACTIVE);
        ManagedPreviewRouteResponse route = vmClient.createRoute(worker.getVmId(), preview.getSubdomain(), preview.getInternalPort());
        preview = repository.save(preview.routed(route.hostname(), route.dnsRecordId()));
        try {
            DeploymentTargetEntity target = targetService.create(worker.getSshKeyRef(), preview.getUserId(), ownerEmail,
                    normalizedName(targetName, preview.getId()), "", "", null, artifact, null,
                    "/opt/gamjabox/previews/" + preview.getId(), false);
            RepoConfig repo = new RepoConfig(null, null, null, null, "/opt/gamjabox/previews/" + preview.getId());
            DeploymentEntity deployment = deploymentExecutor.enqueueManagedForTarget(
                    worker.getSshKeyRef(), worker.getInternalIp(), target, repo, artifact);
            preview = repository.save(preview.queued(target.getId(), deployment.getId()));
            return ManagedPreviewResponse.from(preview);
        } catch (RuntimeException error) {
            try { vmClient.deleteRoute(worker.getVmId(), preview.getSubdomain(), preview.getDnsRecordId()); }
            catch (RuntimeException cleanupError) { log.error("배포 실패 후 Preview 라우트 정리 실패: id={}", preview.getId(), cleanupError); }
            repository.save(preview.status(ManagedPreviewStatus.FAILED, safeMessage(error)));
            throw error;
        }
    }

    @Transactional
    public ManagedPreviewResponse get(String id, String userId) {
        ManagedPreviewDeploymentEntity preview = owned(id, userId);
        return ManagedPreviewResponse.from(refresh(preview));
    }

    @Transactional
    public List<ManagedPreviewResponse> list(String userId) {
        return repository.findAllByUserIdOrderByCreatedAtDesc(userId).stream().map(this::refresh)
                .map(ManagedPreviewResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public Optional<DeploymentEntity> findDeployment(String deploymentId) {
        return deploymentId == null ? Optional.empty() : deploymentRepository.findById(deploymentId);
    }

    private ManagedPreviewDeploymentEntity refresh(ManagedPreviewDeploymentEntity preview) {
        if (preview.getDeploymentId() == null || !OCCUPYING.contains(preview.getStatus())) return preview;
        DeploymentEntity deployment = deploymentRepository.findById(preview.getDeploymentId()).orElse(null);
        if (deployment == null) return preview;
        ManagedPreviewStatus mapped = switch (deployment.getStatus()) {
            case QUEUED -> ManagedPreviewStatus.QUEUED;
            case SUCCEEDED -> ManagedPreviewStatus.RUNNING;
            case FAILED, ROLLED_BACK -> ManagedPreviewStatus.FAILED;
            case STOPPED, STOPPING -> ManagedPreviewStatus.STOPPED;
            default -> ManagedPreviewStatus.BUILDING;
        };
        if (mapped == preview.getStatus()) return preview;
        if (mapped == ManagedPreviewStatus.FAILED && preview.getDnsRecordId() != null) {
            try {
                SystemWorkerEntity worker = systemWorkerService.requireActive();
                vmClient.deleteRoute(worker.getVmId(), preview.getSubdomain(), preview.getDnsRecordId());
            } catch (RuntimeException cleanupError) {
                log.warn("실패 Preview 라우트 정리 재시도 필요: id={}, error={}", preview.getId(), cleanupError.getMessage());
            }
        }
        return repository.save(preview.status(mapped, mapped == ManagedPreviewStatus.FAILED ? deployment.getErrorMessage() : null));
    }

    @Scheduled(fixedDelayString = "${ops.managed-preview.cleanup-interval-ms:60000}")
    public void expire() {
        List<ManagedPreviewDeploymentEntity> expired = repository.findAllByExpiresAtBeforeAndStatusIn(LocalDateTime.now(), OCCUPYING);
        for (ManagedPreviewDeploymentEntity preview : expired) {
            try { cleanup(preview, ManagedPreviewStatus.EXPIRED); }
            catch (RuntimeException e) { log.warn("만료 Preview 정리 재시도 필요: id={}, error={}", preview.getId(), e.getMessage()); }
        }
    }

    private void cleanup(ManagedPreviewDeploymentEntity preview, ManagedPreviewStatus terminalStatus) {
        systemWorkerService.requireActive();
        SystemWorkerEntity worker = systemWorkerService.requireActive();
        if (preview.getDeploymentTargetId() != null && preview.getDeploymentTargetId().matches("[a-f0-9-]{36}")) {
            Session session = sessionFactory.createSession(worker.getSshKeyRef(), worker.getInternalIp());
            try {
                String target = preview.getDeploymentTargetId();
                String resolved = "/home/ubuntu/gamjabox/apps/" + target + "/releases/"
                        + preview.getDeploymentId() + "/resolved-compose.yml";
                String command = "if test -f '" + resolved + "'; then docker compose -p gj_" + target
                        + " -f '" + resolved + "' down --rmi all; else docker rm -f '"
                        + preview.getContainerName() + "' >/dev/null 2>&1 || true; fi; sudo rm -rf '/opt/gamjabox/previews/"
                        + preview.getId() + "' '/home/ubuntu/gamjabox/apps/" + target + "'";
                commands.execOrThrow(session, command, 300_000);
            } catch (Exception e) { log.warn("만료 Preview Runtime 정리 실패: id={}, error={}", preview.getId(), e.getMessage()); }
            finally { session.disconnect(); }
        }
        vmClient.deleteRoute(worker.getVmId(), preview.getSubdomain(), preview.getDnsRecordId());
        repository.save(preview.status(terminalStatus, null));
    }

    private ManagedPreviewDeploymentEntity owned(String id, String userId) { return repository.findByIdAndUserId(id, userId).orElseThrow(() -> new OpsException(OpsErrorCode.MANAGED_PREVIEW_NOT_FOUND)); }
    private String normalizedName(String requested, String id) { String base = requested == null || requested.isBlank() ? "Managed Preview" : requested.trim(); return base.substring(0, Math.min(base.length(), 40)) + "-" + id.substring(0, 8); }
    private String safeMessage(Exception e) { return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage().substring(0, Math.min(1000, e.getMessage().length())); }
}
