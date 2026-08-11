package gj.cloud.ops.application.deployment.service;

import gj.cloud.ops.application.deployment.dto.OrphanReconcileResult;
import gj.cloud.ops.application.vmclient.VmAutomationClient;
import gj.cloud.ops.domain.deployment.repository.ActiveDeploymentTargetVmRef;
import gj.cloud.ops.domain.deployment.repository.DeploymentTargetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeploymentOrphanReconciler {
    public static final String REASON_VM_NOT_FOUND = "VM_NOT_FOUND";
    private static final int VM_EXISTENCE_BATCH_SIZE = 500;

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
        List<ActiveDeploymentTargetVmRef> targets = targetRepository.findActiveTargetVmRefs();
        int scanned = targets.size();
        int missing = 0;
        int hardDeleted = 0;
        int quarantined = 0;
        int errors = 0;

        Map<String, List<ActiveDeploymentTargetVmRef>> targetsByVm = new LinkedHashMap<>();
        for (ActiveDeploymentTargetVmRef target : targets) {
            try {
                UUID.fromString(target.vmId());
                targetsByVm.computeIfAbsent(target.vmId(), ignored -> new ArrayList<>()).add(target);
            } catch (IllegalArgumentException error) {
                errors++;
                log.warn("배포 대상의 VM ID 형식이 잘못되어 고아 검사를 건너뜁니다: targetId={}, vmId={}",
                        target.targetId(), target.vmId());
            }
        }

        List<String> vmIds = new ArrayList<>(targetsByVm.keySet());
        for (int from = 0; from < vmIds.size(); from += VM_EXISTENCE_BATCH_SIZE) {
            List<String> batch = vmIds.subList(from, Math.min(from + VM_EXISTENCE_BATCH_SIZE, vmIds.size()));
            Set<String> existing;
            try {
                existing = vmAutomationClient.findExistingVmIds(Set.copyOf(batch));
            } catch (Exception error) {
                int affectedTargets = batch.stream().mapToInt(vmId -> targetsByVm.get(vmId).size()).sum();
                errors += affectedTargets;
                log.warn("VM 존재 여부 일괄 조회 실패로 고아 검사를 건너뜁니다: vmCount={}, targetCount={}, error={}",
                        batch.size(), affectedTargets, error.getMessage());
                continue;
            }

            for (String vmId : batch) {
                if (existing.contains(vmId)) continue;
                for (ActiveDeploymentTargetVmRef target : targetsByVm.get(vmId)) {
                    missing++;
                    try {
                        var action = mutationService.handleMissingVm(target.targetId(), REASON_VM_NOT_FOUND);
                        if (action == DeploymentOrphanMutationService.CleanupAction.HARD_DELETED) hardDeleted++;
                        if (action == DeploymentOrphanMutationService.CleanupAction.QUARANTINED) quarantined++;
                    } catch (Exception cleanupError) {
                        errors++;
                        log.warn("배포 대상 고아 정리 실패: targetId={}, vmId={}, error={}",
                                target.targetId(), target.vmId(), cleanupError.getMessage());
                    }
                }
            }
        }
        return new OrphanReconcileResult(scanned, missing, hardDeleted, quarantined, errors);
    }

    public void handleConfirmedMissingVm(String targetId) {
        mutationService.handleMissingVm(targetId, REASON_VM_NOT_FOUND);
    }
}
