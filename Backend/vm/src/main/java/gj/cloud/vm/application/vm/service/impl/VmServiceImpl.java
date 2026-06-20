package gj.cloud.vm.application.vm.service.impl;

import gj.cloud.vm.application.port.service.PortService;
import gj.cloud.vm.application.ssh.client.UserServiceClient;
import gj.cloud.vm.application.vm.dto.VmAvailabilityResponse;
import gj.cloud.vm.application.vm.dto.VmAvailabilityResponse.PlanAvailability;
import gj.cloud.vm.application.vm.dto.VmCreateRequest;
import gj.cloud.vm.application.vm.dto.VmPlanUpdateRequest;
import gj.cloud.vm.application.vm.dto.VmPowerRequest;
import gj.cloud.vm.application.vm.dto.VmResponse;
import gj.cloud.vm.application.vm.dto.VmStatusEvent;
import gj.cloud.vm.application.vm.service.VmService;
import gj.cloud.vm.domain.port.entity.VmSshAccessEmailEntity;
import gj.cloud.vm.domain.port.repository.VmSshAccessEmailRepository;
import gj.cloud.vm.domain.vm.entity.VmEntity;
import gj.cloud.vm.domain.vm.enums.PlanType;
import gj.cloud.vm.domain.vm.enums.VmStatus;
import gj.cloud.vm.domain.vm.repository.VmRepository;
import gj.cloud.vm.global.exception.VmException;
import gj.cloud.vm.global.exception.enums.VmErrorCode;
import gj.cloud.vm.global.sse.SseEmitterManager;
import gj.cloud.vm.infra.cloudflare.client.CloudflareClient;
import gj.cloud.vm.infra.proxmox.client.ProxmoxClient;
import gj.cloud.vm.infra.proxmox.config.ProxmoxProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VmServiceImpl implements VmService {

    private static final int SSH_EMAIL_MAX_COUNT = 10;

    private final VmRepository vmRepository;
    private final ProxmoxClient proxmoxClient;
    private final CloudflareClient cloudflareClient;
    private final UserServiceClient userServiceClient;
    private final SseEmitterManager sseEmitterManager;
    private final ProxmoxProperties proxmoxProperties;
    private final VmSshAccessEmailRepository sshAccessEmailRepository;
    private final PortService portService;

    @Override
    public Mono<VmResponse> createVm(String userId, String bearerToken, VmCreateRequest request) {
        return createVmWithEmail(userId, bearerToken, request, null);
    }

    @Override
    public Mono<VmResponse> createVmWithEmail(String userId, String bearerToken, VmCreateRequest request, String ownerEmail) {
        PlanType plan = request.planType();
        int disk = request.diskSizeGb();
        if (disk < plan.getMinDiskGb() || disk > plan.getMaxDiskGb()) {
            return Mono.error(new VmException(VmErrorCode.INVALID_DISK_SIZE));
        }
        return Mono.defer(() -> {
            VmEntity pending = VmEntity.createPending(userId, request.name(), plan, request.sshKeyId(), disk);
            return vmRepository.save(pending);
        })
        .retryWhen(Retry.max(3).filter(e -> e instanceof DataIntegrityViolationException))
        .doOnNext(saved -> provisionVm(saved, bearerToken, ownerEmail)
                .subscribe(
                        null,
                        error -> log.error("프로비저닝 실패: vmId={}, error={}", saved.getId(), error.getMessage())
                ))
        .map(VmResponse::from);
    }

    private Mono<Void> provisionVm(VmEntity vm, String bearerToken, String ownerEmail) {
        return updateStatus(vm, VmStatus.CREATING)
                .flatMap(creating ->
                        Mono.zip(
                                userServiceClient.getSshKey(bearerToken, creating.getSshKeyId()),
                                allocateVmId(),
                                allocateIp(creating.getPlanType())
                        )
                        .flatMap(tuple -> {
                            String sshPublicKey = tuple.getT1().publicKey();
                            int newVmid = tuple.getT2();
                            String staticIp = tuple.getT3();
                            return proxmoxClient.ensurePoolExists(proxmoxProperties.getPool())
                                    .then(proxmoxClient.cloneVm(newVmid, creating.getPlanType(), creating.getName()))
                                    .flatMap(taskId -> vmRepository.save(creating.withVmidAndTaskId(newVmid, taskId)))
                                    .map(saved -> new Object[]{saved, sshPublicKey, staticIp});
                        })
                )
                .flatMap(arr -> {
                    VmEntity cloned = (VmEntity) arr[0];
                    String sshPublicKey = (String) arr[1];
                    String staticIp = (String) arr[2];
                    return updateStatus(cloned, VmStatus.BOOTING)
                            .flatMap(booting ->
                                    proxmoxClient.waitForTaskCompletion(booting.getProxmoxTaskId())
                                            .then(proxmoxClient.configureVm(booting.getVmid(), booting.getPlanType(), sshPublicKey, staticIp))
                                            .then(proxmoxClient.resizeDisk(booting.getVmid(), booting.getDiskSizeGb()))
                                            .then(proxmoxClient.startVm(booting.getVmid()))
                                            .then(proxmoxClient.waitForIpAssignment(booting.getVmid()))
                                            .flatMap(ip -> {
                                                VmEntity running = booting.withRunning(ip);
                                                return vmRepository.save(running)
                                                        .flatMap(saved -> setupCloudflare(saved, ownerEmail));
                                            })
                            );
                })
                .onErrorResume(e -> {
                    log.error("프로비저닝 오류: vmId={}, error={}", vm.getId(), e.getMessage());
                    return vmRepository.findById(vm.getId())
                            .flatMap(current -> {
                                VmEntity failed = current.withFailed(e.getMessage());
                                return vmRepository.save(failed)
                                        .doOnNext(saved -> publishEvent(saved, e.getMessage()));
                            });
                })
                .then();
    }

    private Mono<VmEntity> setupCloudflare(VmEntity vm, String ownerEmail) {
        if (vm.getSubdomain() == null) {
            publishEvent(vm, null);
            return Mono.just(vm);
        }
        return cloudflareClient.registerCname(vm.getSubdomain())
                .flatMap(dnsRecordId ->
                        cloudflareClient.addIngressRule(vm.getSubdomain(), vm.getInternalIp())
                                .then(cloudflareClient.createAccessApp(vm.getSubdomain()))
                                .flatMap(appId -> {
                                    if (ownerEmail != null) {
                                        return cloudflareClient.createAccessPolicy(appId, List.of(ownerEmail))
                                                .flatMap(policyId ->
                                                        vmRepository.save(vm.withCloudflareIds(dnsRecordId, appId, policyId))
                                                                .flatMap(saved ->
                                                                        sshAccessEmailRepository.save(
                                                                                VmSshAccessEmailEntity.create(saved.getId(), ownerEmail))
                                                                                .thenReturn(saved)
                                                                )
                                                );
                                    }
                                    return vmRepository.save(vm.withCloudflareIds(dnsRecordId, appId, null));
                                })
                )
                .doOnNext(saved -> publishEvent(saved, null))
                .onErrorResume(e -> {
                    log.error("Cloudflare 설정 실패: vmId={}, error={}", vm.getId(), e.getMessage());
                    publishEvent(vm, null);
                    return Mono.just(vm);
                });
    }

    @Override
    public Flux<VmResponse> getVms(String userId) {
        return vmRepository.findAllByUserIdAndNotDeleted(userId)
                .map(VmResponse::from);
    }

    @Override
    public Mono<VmResponse> getVm(String userId, UUID vmId) {
        return vmRepository.findById(vmId)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND)))
                .flatMap(vm -> {
                    if (vm.getDeletedAt() != null) {
                        return Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND));
                    }
                    if (!vm.getUserId().equals(userId)) {
                        return Mono.error(new VmException(VmErrorCode.FORBIDDEN));
                    }
                    if (vm.getStatus() != VmStatus.RUNNING || vm.getVmid() == null) {
                        return Mono.just(VmResponse.from(vm, null));
                    }
                    return proxmoxClient.needsReboot(vm.getVmid())
                            .map(needsReboot -> VmResponse.from(vm, needsReboot));
                });
    }

    @Override
    public Mono<Void> deleteVm(String userId, UUID vmId) {
        return vmRepository.findById(vmId)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND)))
                .flatMap(vm -> {
                    if (vm.getDeletedAt() != null) {
                        return Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND));
                    }
                    if (!vm.getUserId().equals(userId)) {
                        return Mono.error(new VmException(VmErrorCode.FORBIDDEN));
                    }
                    return Mono.just(vm);
                })
                .flatMap(vm -> updateStatus(vm, VmStatus.DELETING))
                .flatMap(vm -> {
                    Mono<Void> cloudflareTeardown = teardownCloudflare(vm);
                    if (vm.getVmid() == null) {
                        return cloudflareTeardown
                                .then(vmRepository.save(vm.withDeleted()))
                                .doOnNext(saved -> publishEvent(saved, null))
                                .then();
                    }
                    return cloudflareTeardown
                            .then(proxmoxClient.deleteVm(vm.getVmid()))
                            .then(vmRepository.save(vm.withDeleted()))
                            .doOnNext(saved -> publishEvent(saved, null))
                            .onErrorResume(e -> {
                                log.error("Proxmox VM 삭제 실패, 수동 확인 필요: vmid={}, error={}", vm.getVmid(), e.getMessage());
                                return Mono.error(new VmException(VmErrorCode.PROXMOX_DELETE_FAILED));
                            })
                            .then();
                });
    }

    private Mono<Void> teardownCloudflare(VmEntity vm) {
        Mono<Void> portsTeardown = portService.teardownAllPortsForVm(vm.getId());
        Mono<Void> sshEmailCleanup = sshAccessEmailRepository.deleteAllByVmId(vm.getId());
        Mono<Void> sshTeardown;

        if (vm.getCfPolicyId() != null && vm.getCfAppId() != null) {
            sshTeardown = cloudflareClient.deleteAccessPolicy(vm.getCfAppId(), vm.getCfPolicyId())
                    .then(cloudflareClient.deleteAccessApp(vm.getCfAppId()))
                    .then(vm.getSubdomain() != null ? cloudflareClient.removeIngressRule(vm.getSubdomain()) : Mono.empty())
                    .then(vm.getCfDnsRecordId() != null ? cloudflareClient.deleteCname(vm.getCfDnsRecordId()) : Mono.empty());
        } else if (vm.getCfAppId() != null) {
            sshTeardown = cloudflareClient.deleteAccessApp(vm.getCfAppId())
                    .then(vm.getSubdomain() != null ? cloudflareClient.removeIngressRule(vm.getSubdomain()) : Mono.empty())
                    .then(vm.getCfDnsRecordId() != null ? cloudflareClient.deleteCname(vm.getCfDnsRecordId()) : Mono.empty());
        } else if (vm.getSubdomain() != null) {
            sshTeardown = cloudflareClient.removeIngressRule(vm.getSubdomain())
                    .then(vm.getCfDnsRecordId() != null ? cloudflareClient.deleteCname(vm.getCfDnsRecordId()) : Mono.empty());
        } else {
            sshTeardown = Mono.empty();
        }

        return portsTeardown.then(sshEmailCleanup).then(sshTeardown);
    }

    @Override
    public Mono<VmResponse> changePower(String userId, UUID vmId, VmPowerRequest request) {
        return vmRepository.findById(vmId)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND)))
                .flatMap(vm -> {
                    if (vm.getDeletedAt() != null) return Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND));
                    if (!vm.getUserId().equals(userId)) return Mono.error(new VmException(VmErrorCode.FORBIDDEN));
                    if (vm.getVmid() == null) return Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND));
                    return switch (request.action()) {
                        case START -> updateStatus(vm, VmStatus.STARTING)
                                .flatMap(v -> proxmoxClient.startVm(v.getVmid())
                                        .then(updateStatus(v, VmStatus.RUNNING)));
                        case STOP -> updateStatus(vm, VmStatus.STOPPING)
                                .flatMap(v -> proxmoxClient.stopVm(v.getVmid())
                                        .then(updateStatus(v, VmStatus.STOPPED)));
                        case SUSPEND -> updateStatus(vm, VmStatus.SUSPENDING)
                                .flatMap(v -> proxmoxClient.suspendVm(v.getVmid())
                                        .then(updateStatus(v, VmStatus.SUSPENDED)));
                        case REBOOT -> proxmoxClient.rebootVm(vm.getVmid())
                                .then(updateStatus(vm, VmStatus.RUNNING));
                    };
                })
                .map(VmResponse::from);
    }

    @Override
    public Mono<VmResponse> updatePlan(String userId, UUID vmId, VmPlanUpdateRequest request) {
        return vmRepository.findById(vmId)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND)))
                .flatMap(vm -> {
                    if (vm.getDeletedAt() != null) return Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND));
                    if (!vm.getUserId().equals(userId)) return Mono.error(new VmException(VmErrorCode.FORBIDDEN));
                    return validatePlanChange(vm, request);
                })
                .flatMap(vm -> {
                    Mono<Void> resourceUpdate = vm.getVmid() != null
                            ? proxmoxClient.updateResources(vm.getVmid(), request.planType())
                            : Mono.empty();
                    Mono<Void> diskResize = (vm.getVmid() != null && request.diskSizeGb() > vm.getDiskSizeGb())
                            ? proxmoxClient.resizeDisk(vm.getVmid(), request.diskSizeGb())
                            : Mono.empty();
                    return resourceUpdate
                            .then(diskResize)
                            .then(vmRepository.save(vm.withPlanChange(request.planType(), request.diskSizeGb())))
                            .doOnNext(saved -> publishEvent(saved, null))
                            .flatMap(saved -> {
                                if (saved.getVmid() == null) return Mono.just(VmResponse.from(saved, null));
                                return proxmoxClient.needsReboot(saved.getVmid())
                                        .map(needsReboot -> VmResponse.from(saved, needsReboot));
                            });
                });
    }

    private Mono<VmEntity> validatePlanChange(VmEntity vm, VmPlanUpdateRequest request) {
        int newDisk = request.diskSizeGb();
        PlanType newPlan = request.planType();

        if (newDisk < vm.getDiskSizeGb()) {
            return Mono.error(new VmException(VmErrorCode.DISK_DOWNSIZE_NOT_ALLOWED));
        }
        if (newDisk < newPlan.getMinDiskGb() || newDisk > newPlan.getMaxDiskGb()) {
            return Mono.error(new VmException(VmErrorCode.INVALID_DISK_SIZE));
        }
        // PRO → FREE 다운그레이드: 현재 디스크가 FREE maxDisk 초과하면 불가
        if (newPlan == PlanType.FREE && vm.getDiskSizeGb() > PlanType.FREE.getMaxDiskGb()) {
            return Mono.error(new VmException(VmErrorCode.DOWNGRADE_DISK_TOO_LARGE));
        }
        return Mono.just(vm);
    }

    private Mono<VmEntity> updateStatus(VmEntity entity, VmStatus status) {
        VmEntity updated = entity.withStatus(status);
        return vmRepository.save(updated)
                .doOnNext(saved -> publishEvent(saved, null));
    }

    private void publishEvent(VmEntity entity, String errorMessage) {
        VmStatusEvent event = new VmStatusEvent(
                entity.getId().toString(),
                entity.getStatus().name(),
                entity.getInternalIp(),
                errorMessage,
                LocalDateTime.now()
        );
        sseEmitterManager.publish(entity.getUserId(), event);
    }

    private Mono<Integer> allocateVmId() {
        return Mono.zip(
                proxmoxClient.getProxmoxVmids(),
                vmRepository.findAllActiveVmids().collectList()
        ).map(tuple -> {
            Set<Integer> usedOnProxmox = tuple.getT1();
            Set<Integer> usedInDb = new HashSet<>(tuple.getT2());
            for (int i = proxmoxProperties.getVmidRangeStart(); i <= proxmoxProperties.getVmidRangeEnd(); i++) {
                if (!usedOnProxmox.contains(i) && !usedInDb.contains(i)) return i;
            }
            throw new VmException(VmErrorCode.VMID_ALLOCATION_FAILED);
        });
    }

    private Mono<String> allocateIp(PlanType planType) {
        List<String> pool = planType.getIpPool();
        return vmRepository.findAllActiveInternalIps()
                .collect(Collectors.toSet())
                .map(usedIps -> {
                    for (String ip : pool) {
                        if (!usedIps.contains(ip)) return ip;
                    }
                    throw new VmException(VmErrorCode.IP_POOL_EXHAUSTED);
                });
    }

    @Override
    public Mono<List<String>> getSshAccessEmails(String userId, UUID vmId) {
        return vmRepository.findById(vmId)
                .filter(vm -> vm.getUserId().equals(userId) && vm.getDeletedAt() == null)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND)))
                .then(sshAccessEmailRepository.findAllByVmId(vmId)
                        .map(VmSshAccessEmailEntity::getEmail)
                        .collectList()
                );
    }

    @Override
    public Mono<List<String>> addSshAccessEmail(String userId, String ownerEmail, UUID vmId, String email) {
        return vmRepository.findById(vmId)
                .filter(vm -> vm.getUserId().equals(userId) && vm.getDeletedAt() == null)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND)))
                .flatMap(vm -> {
                    if (vm.getCfAppId() == null || vm.getCfPolicyId() == null) {
                        return Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND));
                    }
                    return sshAccessEmailRepository.countByVmId(vmId)
                            .flatMap(count -> {
                                if (count >= SSH_EMAIL_MAX_COUNT) {
                                    return Mono.error(new VmException(VmErrorCode.EMAIL_LIMIT_EXCEEDED));
                                }
                                return sshAccessEmailRepository.save(
                                        VmSshAccessEmailEntity.create(vmId, email));
                            })
                            .then(sshAccessEmailRepository.findAllByVmId(vmId)
                                    .map(VmSshAccessEmailEntity::getEmail)
                                    .collectList()
                            )
                            .flatMap(emails -> cloudflareClient.updateAccessPolicy(
                                    vm.getCfAppId(), vm.getCfPolicyId(), emails)
                                    .thenReturn(emails)
                            );
                });
    }

    @Override
    public Mono<List<String>> removeSshAccessEmail(String userId, String ownerEmail, UUID vmId, String email) {
        if (email.equals(ownerEmail)) {
            return Mono.error(new VmException(VmErrorCode.OWNER_EMAIL_REMOVAL_NOT_ALLOWED));
        }
        return vmRepository.findById(vmId)
                .filter(vm -> vm.getUserId().equals(userId) && vm.getDeletedAt() == null)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND)))
                .flatMap(vm -> {
                    if (vm.getCfAppId() == null || vm.getCfPolicyId() == null) {
                        return Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND));
                    }
                    return sshAccessEmailRepository.findByVmIdAndEmail(vmId, email)
                            .switchIfEmpty(Mono.error(new VmException(VmErrorCode.PORT_NOT_FOUND)))
                            .flatMap(entry -> sshAccessEmailRepository.delete(entry))
                            .then(sshAccessEmailRepository.findAllByVmId(vmId)
                                    .map(VmSshAccessEmailEntity::getEmail)
                                    .collectList()
                            )
                            .flatMap(remaining -> cloudflareClient.updateAccessPolicy(
                                    vm.getCfAppId(), vm.getCfPolicyId(), remaining)
                                    .thenReturn(remaining)
                            );
                });
    }

    @Override
    public Mono<VmAvailabilityResponse> getAvailability() {
        int freeTotal = PlanType.FREE.getIpPool().size();
        int proTotal = PlanType.PRO.getIpPool().size();

        return Mono.zip(
                vmRepository.countActiveByPlanTypeFree(),
                vmRepository.countActiveByPlanTypePro()
        ).map(tuple -> {
            int freeUsed = tuple.getT1().intValue();
            int proUsed = tuple.getT2().intValue();
            return new VmAvailabilityResponse(
                    new PlanAvailability(freeUsed, freeTotal, freeUsed >= freeTotal),
                    new PlanAvailability(proUsed, proTotal, proUsed >= proTotal)
            );
        });
    }
}
