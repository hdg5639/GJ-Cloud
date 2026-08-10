package gj.cloud.ops.application.deployment.service;

import gj.cloud.ops.application.deployment.dto.OrphanReconcileResult;
import gj.cloud.ops.application.vmclient.VmAutomationClient;
import gj.cloud.ops.domain.deployment.entity.DeploymentTargetEntity;
import gj.cloud.ops.domain.deployment.repository.DeploymentTargetRepository;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeploymentOrphanReconciler {
    public static final String REASON_VM_NOT_FOUND = "VM_NOT_FOUND";

    private final DeploymentTargetRepository targetRepository;
    private final VmAutomationClient vmAutomationClient;
    private final DeploymentOrphanMutationService mutationService;

    @Scheduled(fixedDelayString = "${ops.deployment-orphan.reconcile-interval-ms:300000}")
    public void scheduledReconcile() {
        OrphanReconcileResult result = reconcileNow();
        if (result.missing() > 0 || result.errors() > 0) {
            log.info("배포 대상 고아 조정 완료: scanned={}, missing={}, hardDeleted={}, quarantined={}, errors={}",
                    result.scanned(), result.missing(), result.hardDeleted(), result.quarantined(), result.errors());
        }
    }

    public OrphanReconcileResult reconcileNow() {
        int scanned = 0;
        int missing = 0;
        int hardDeleted = 0;
        int quarantined = 0;
        int errors = 0;

        for (DeploymentTargetEntity target : targetRepository.findAllByActiveTrueOrderByCreatedAtAsc()) {
            scanned++;
            try {
                vmAutomationClient.getContext(target.getVmId(), target.getOwnerUserId(), target.getOwnerEmail());
            } catch (OpsException error) {
                if (error.getErrorCode() != OpsErrorCode.VM_NOT_FOUND) {
                    errors++;
                    continue;
                }
                missing++;
                try {
                    var action = mutationService.handleMissingVm(target.getId(), REASON_VM_NOT_FOUND);
                    if (action == DeploymentOrphanMutationService.CleanupAction.HARD_DELETED) hardDeleted++;
                    if (action == DeploymentOrphanMutationService.CleanupAction.QUARANTINED) quarantined++;
                } catch (Exception cleanupError) {
                    errors++;
                    log.warn("배포 대상 고아 정리 실패: targetId={}, vmId={}, error={}",
                            target.getId(), target.getVmId(), cleanupError.getMessage());
                }
            } catch (Exception error) {
                errors++;
                log.warn("배포 대상 고아 검사 실패: targetId={}, vmId={}, error={}",
                        target.getId(), target.getVmId(), error.getMessage());
            }
        }
        return new OrphanReconcileResult(scanned, missing, hardDeleted, quarantined, errors);
    }

    public void handleConfirmedMissingVm(String targetId) {
        mutationService.handleMissingVm(targetId, REASON_VM_NOT_FOUND);
    }
}
