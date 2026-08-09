package gj.cloud.vm.application.systemworker;

import gj.cloud.vm.application.systemworker.dto.SystemWorkerProvisionRequest;
import gj.cloud.vm.application.systemworker.dto.SystemWorkerVmResponse;
import gj.cloud.vm.global.exception.VmException;
import gj.cloud.vm.global.exception.enums.VmErrorCode;
import gj.cloud.vm.infra.proxmox.client.ProxmoxClient;
import gj.cloud.vm.infra.proxmox.config.ProxmoxProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemWorkerProvisioningService {
    private static final String ROLE = "AUTO_PREVIEW";

    private final ProxmoxClient proxmoxClient;
    private final ProxmoxProperties proxmoxProperties;
    private final SystemWorkerProperties properties;

    public Mono<SystemWorkerVmResponse> provision(SystemWorkerProvisionRequest request) {
        validateFixedSpecification(request);
        AtomicBoolean cloneCompleted = new AtomicBoolean(false);
        return proxmoxClient.getProxmoxVmids().flatMap(vmids -> {
            if (vmids.contains(request.vmId())) {
                return Mono.error(new VmException(VmErrorCode.SYSTEM_WORKER_ALREADY_EXISTS));
            }
            return proxmoxClient.ensurePoolExists(properties.getPool())
                    .then(proxmoxClient.cloneVm(request.vmId(), request.templateVmid(), properties.getName(), properties.getPool()))
                    .flatMap(task -> proxmoxClient.waitForTaskCompletion(task)
                            .doOnSuccess(ignored -> cloneCompleted.set(true)))
                    .then(proxmoxClient.configureVm(request.vmId(), request.cores(), request.memoryMb(),
                            List.of(request.sshPublicKey())))
                    .then(proxmoxClient.resizeDisk(request.vmId(), request.diskGb()))
                    .then(proxmoxClient.startVm(request.vmId()))
                    .then(proxmoxClient.waitForIpAssignment(request.vmId()))
                    .flatMap(ip -> response(request.vmId(), ip))
                    .onErrorResume(error -> cloneCompleted.get()
                            ? proxmoxClient.deleteVm(request.vmId()).onErrorResume(cleanup -> Mono.empty()).then(Mono.error(error))
                            : Mono.error(error));
        });
    }

    public Mono<SystemWorkerVmResponse> status(int vmId) {
        validateVmid(vmId);
        return proxmoxClient.getProxmoxVmids().flatMap(vmids -> {
            if (!vmids.contains(vmId)) return Mono.just(missing(vmId));
            return proxmoxClient.getSystemVmInfo(vmId).flatMap(info -> {
                if (!properties.getName().equals(info.name())) {
                    log.error("예약 VMID에 다른 VM이 존재함: vmid={}, name={}", vmId, info.name());
                    return Mono.error(new VmException(VmErrorCode.SYSTEM_WORKER_IDENTITY_MISMATCH));
                }
                if (!"running".equalsIgnoreCase(info.powerState())) {
                    return Mono.just(new SystemWorkerVmResponse(true, vmId, proxmoxProperties.getNode(), null,
                            info.powerState().toUpperCase(), info.cores(), info.memoryMb(), info.diskGb()));
                }
                return proxmoxClient.waitForIpAssignment(vmId)
                        .map(ip -> toResponse(vmId, ip, info))
                        .onErrorReturn(toResponse(vmId, null, info));
            });
        });
    }

    public Mono<SystemWorkerVmResponse> start(int vmId) { validateVmid(vmId); return proxmoxClient.startVm(vmId).then(status(vmId)); }
    public Mono<SystemWorkerVmResponse> stop(int vmId) { validateVmid(vmId); return proxmoxClient.stopVm(vmId).then(status(vmId)); }
    public Mono<SystemWorkerVmResponse> reboot(int vmId) { validateVmid(vmId); return proxmoxClient.rebootVm(vmId).then(status(vmId)); }

    private Mono<SystemWorkerVmResponse> response(int vmId, String ip) {
        return proxmoxClient.getSystemVmInfo(vmId).map(info -> toResponse(vmId, ip, info));
    }

    private SystemWorkerVmResponse toResponse(int vmId, String ip, ProxmoxClient.SystemVmInfo info) {
        return new SystemWorkerVmResponse(true, vmId, proxmoxProperties.getNode(), ip,
                info.powerState().toUpperCase(), info.cores(), info.memoryMb(), info.diskGb());
    }

    private void validateFixedSpecification(SystemWorkerProvisionRequest r) {
        if (!properties.isEnabled() || !ROLE.equals(r.role()) || r.vmId() != properties.getPreferredVmid()
                || r.cores() != properties.getCores() || r.memoryMb() != properties.getMemoryMb()
                || r.diskGb() != properties.getDiskGb() || r.templateVmid() != properties.getTemplateVmid()) {
            throw new VmException(VmErrorCode.INVALID_SYSTEM_WORKER_SPEC);
        }
    }

    private void validateVmid(int vmId) {
        if (!properties.isEnabled() || vmId != properties.getPreferredVmid()) {
            throw new VmException(VmErrorCode.INVALID_SYSTEM_WORKER_SPEC);
        }
    }

    public void validateWorkerId(int vmId) { validateVmid(vmId); }

    private SystemWorkerVmResponse missing(int vmId) {
        return new SystemWorkerVmResponse(false, vmId, proxmoxProperties.getNode(), null, "MISSING",
                properties.getCores(), properties.getMemoryMb(), properties.getDiskGb());
    }
}
