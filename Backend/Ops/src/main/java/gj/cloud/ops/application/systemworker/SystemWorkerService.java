package gj.cloud.ops.application.systemworker;

import gj.cloud.ops.application.managementkey.service.ManagementKeyService;
import gj.cloud.ops.application.systemworker.dto.SystemWorkerResponse;
import gj.cloud.ops.application.systemworker.dto.SystemWorkerVmResponse;
import gj.cloud.ops.domain.systemworker.entity.SystemWorkerEntity;
import gj.cloud.ops.domain.systemworker.enums.SystemWorkerRole;
import gj.cloud.ops.domain.systemworker.enums.SystemWorkerStatus;
import gj.cloud.ops.domain.systemworker.repository.SystemWorkerRepository;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@Service
public class SystemWorkerService {
    private final SystemWorkerRepository repository;
    private final SystemWorkerProperties properties;
    private final ManagementKeyService keyService;
    private final SystemWorkerVmClient vmClient;
    private final SystemWorkerRuntimeService runtime;
    private final TaskExecutor executor;

    public SystemWorkerService(SystemWorkerRepository repository, SystemWorkerProperties properties,
            ManagementKeyService keyService, SystemWorkerVmClient vmClient, SystemWorkerRuntimeService runtime,
            @Qualifier("deploymentTaskExecutor") TaskExecutor executor) {
        this.repository = repository; this.properties = properties; this.keyService = keyService;
        this.vmClient = vmClient; this.runtime = runtime; this.executor = executor;
    }

    @Transactional
    public SystemWorkerResponse get() {
        var worker = repository.findByRole(SystemWorkerRole.AUTO_PREVIEW);
        if (worker.isEmpty()) return SystemWorkerResponse.notConfigured();
        SystemWorkerEntity current = worker.get();
        if (current.getStatus() == SystemWorkerStatus.PROVISIONING) return SystemWorkerResponse.from(current);

        try {
            SystemWorkerVmResponse vm = vmClient.status(current.getVmId());
            if (!vm.exists()) return SystemWorkerResponse.from(repository.save(current.missing(vm.node())));
            if (current.getStatus() == SystemWorkerStatus.MISSING) {
                SystemWorkerStatus recoveredStatus = "RUNNING".equals(vm.powerState())
                        ? SystemWorkerStatus.DEGRADED : SystemWorkerStatus.STOPPED;
                current = repository.save(current.observed(recoveredStatus, vm.node(), vm.internalIp(),
                        "Proxmox VM이 다시 확인되었습니다. Reconcile로 Runtime 상태를 확인해주세요."));
            }
        } catch (Exception e) {
            // ControlBox 자체를 막지 않는다. VM 서비스 장애와 실제 VM 부재를 혼동해 재생성을 허용해서도 안 된다.
            log.warn("Auto Preview Worker 존재 여부 확인 실패, 마지막 저장 상태를 반환합니다: {}", e.getMessage());
        }
        return SystemWorkerResponse.from(current);
    }

    @Transactional
    public SystemWorkerResponse create() {
        var existingWorker = repository.findByRole(SystemWorkerRole.AUTO_PREVIEW);
        boolean reprovisioning = existingWorker.map(this::canReprovision).orElse(false);
        SystemWorkerEntity worker = existingWorker
                .map(existing -> {
                    if (!reprovisioning) throw new OpsException(OpsErrorCode.SYSTEM_WORKER_ALREADY_CONFIGURED);
                    return repository.save(existing.reprovisioning());
                })
                .orElseGet(() -> repository.save(SystemWorkerEntity.provisioning(properties.getName(),
                        properties.getPreferredVmid(), properties.getCores(), properties.getMemoryMb(), properties.getDiskGb())));
        // MISSING VM을 다시 만들 때는 기존 TOFU host fingerprint도 무효하므로 관리 키를 회전한다.
        String publicKey = reprovisioning ? keyService.rotate(worker.getSshKeyRef()) : keyService.issue(worker.getSshKeyRef());
        String workerId = worker.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try { executor.execute(() -> provision(workerId, publicKey)); }
                catch (RuntimeException error) {
                    log.error("Auto Preview Worker 프로비저닝 작업 예약 실패", error);
                    repository.findById(workerId).ifPresent(saved -> repository.save(saved.failed("작업 큐가 요청을 수락하지 못했습니다.")));
                }
            }
        });
        return SystemWorkerResponse.from(worker);
    }

    private boolean canReprovision(SystemWorkerEntity worker) {
        if (worker.getStatus() == SystemWorkerStatus.PROVISIONING) return false;
        return !vmClient.status(worker.getVmId()).exists();
    }

    private void provision(String id, String publicKey) {
        try {
            saveStage(id, "CLONING_VM");
            SystemWorkerVmResponse vm = vmClient.provision(properties, publicKey);
            SystemWorkerEntity worker = repository.findById(id).orElseThrow();
            worker = repository.save(worker.stage("WAITING_SSH").observed(SystemWorkerStatus.PROVISIONING, vm.node(), vm.internalIp(), null));
            runtime.waitUntilReachable(worker);
            saveStage(id, "BOOTSTRAPPING_RUNTIME");
            worker = repository.findById(id).orElseThrow();
            runtime.repair(worker);
            saveStage(id, "VERIFYING_DOCKER");
            worker = repository.findById(id).orElseThrow();
            if (!runtime.healthy(worker)) throw new OpsException(OpsErrorCode.SYSTEM_WORKER_NOT_ACTIVE);
            repository.save(worker.healthy(vm.node(), vm.internalIp()));
        } catch (Exception e) {
            log.error("Auto Preview Worker 프로비저닝 실패", e);
            repository.findById(id).ifPresent(w -> repository.save(w.failed(safeMessage(e))));
        }
    }

    public SystemWorkerResponse reconcile() {
        SystemWorkerEntity worker = requireWorker();
        SystemWorkerVmResponse vm = vmClient.status(worker.getVmId());
        if (!vm.exists()) return SystemWorkerResponse.from(repository.save(worker.missing(vm.node())));
        if (!"RUNNING".equals(vm.powerState())) return SystemWorkerResponse.from(repository.save(worker.observed(SystemWorkerStatus.STOPPED, vm.node(), null, null)));
        SystemWorkerEntity observed = worker.observed(SystemWorkerStatus.DEGRADED, vm.node(), vm.internalIp(), null);
        boolean resourceMatches = vm.cores() == worker.getCores() && vm.memoryMb() == worker.getMemoryMb()
                && vm.diskGb() >= worker.getDiskGb();
        if (!resourceMatches) {
            observed = observed.observed(SystemWorkerStatus.DEGRADED, vm.node(), vm.internalIp(),
                    "워커 리소스가 등록된 사양과 다릅니다.");
        } else if (runtime.healthy(observed)) observed = observed.healthy(vm.node(), vm.internalIp());
        else observed = observed.observed(SystemWorkerStatus.DEGRADED, vm.node(), vm.internalIp(), "SSH 또는 Docker Runtime 검사에 실패했습니다.");
        return SystemWorkerResponse.from(repository.save(observed));
    }

    public SystemWorkerResponse action(String action) {
        SystemWorkerEntity worker = requireWorker();
        SystemWorkerVmResponse vm = vmClient.action(worker.getVmId(), action);
        SystemWorkerStatus status = "STOPPED".equals(vm.powerState()) ? SystemWorkerStatus.STOPPED : SystemWorkerStatus.DEGRADED;
        repository.save(worker.observed(status, vm.node(), vm.internalIp(), null));
        return "stop".equals(action) ? get() : reconcile();
    }

    public SystemWorkerResponse repair() {
        SystemWorkerEntity worker = requireWorker();
        if (worker.getInternalIp() == null) throw new OpsException(OpsErrorCode.SYSTEM_WORKER_NOT_ACTIVE);
        try { runtime.repair(worker); return reconcile(); }
        catch (Exception e) { repository.save(worker.observed(SystemWorkerStatus.DEGRADED, null, null, safeMessage(e))); throw e; }
    }

    @Transactional(readOnly = true)
    public SystemWorkerEntity requireActive() {
        SystemWorkerEntity worker = requireWorker();
        if (worker.getStatus() != SystemWorkerStatus.ACTIVE || worker.getInternalIp() == null) throw new OpsException(OpsErrorCode.SYSTEM_WORKER_NOT_ACTIVE);
        return worker;
    }
    private SystemWorkerEntity requireWorker() { return repository.findByRole(SystemWorkerRole.AUTO_PREVIEW).orElseThrow(() -> new OpsException(OpsErrorCode.SYSTEM_WORKER_NOT_CONFIGURED)); }
    private void saveStage(String id, String stage) { repository.findById(id).ifPresent(w -> repository.save(w.stage(stage))); }
    private String safeMessage(Exception e) { String m = e.getMessage(); return m == null ? e.getClass().getSimpleName() : m.substring(0, Math.min(m.length(), 1000)); }

    @Scheduled(fixedDelayString = "${ops.system-worker.reconcile-interval-ms:60000}")
    public void scheduledReconcile() {
        repository.findByRole(SystemWorkerRole.AUTO_PREVIEW)
                .filter(worker -> worker.getStatus() != SystemWorkerStatus.PROVISIONING)
                .ifPresent(worker -> {
                    try { reconcile(); }
                    catch (Exception e) { log.warn("Auto Preview Worker 주기 조정 실패: {}", e.getMessage()); }
                });
    }
}
