package gj.cloud.vm.application.vm.service.impl;

import gj.cloud.vm.application.ssh.client.UserServiceClient;
import gj.cloud.vm.application.vm.dto.VmCreateRequest;
import gj.cloud.vm.application.vm.dto.VmPowerRequest;
import gj.cloud.vm.application.vm.dto.VmResponse;
import gj.cloud.vm.application.vm.dto.VmStatusEvent;
import gj.cloud.vm.application.vm.service.VmService;
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

    private final VmRepository vmRepository;
    private final ProxmoxClient proxmoxClient;
    private final CloudflareClient cloudflareClient;
    private final UserServiceClient userServiceClient;
    private final SseEmitterManager sseEmitterManager;
    private final ProxmoxProperties proxmoxProperties;

    @Override
    public Mono<VmResponse> createVm(String userId, String bearerToken, VmCreateRequest request) {
        return Mono.defer(() -> {
            VmEntity pending = VmEntity.createPending(userId, request.name(), request.planType(), request.sshKeyId());
            return vmRepository.save(pending);
        })
        .retryWhen(Retry.max(3).filter(e -> e instanceof DataIntegrityViolationException))
        .doOnNext(saved -> provisionVm(saved, bearerToken, null)
                .subscribe(
                        null,
                        error -> log.error("프로비저닝 실패: vmId={}, error={}", saved.getId(), error.getMessage())
                ))
        .map(VmResponse::from);
    }

    public Mono<VmResponse> createVmWithEmail(String userId, String bearerToken, VmCreateRequest request, String ownerEmail) {
        return Mono.defer(() -> {
            VmEntity pending = VmEntity.createPending(userId, request.name(), request.planType(), request.sshKeyId());
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
                                        return cloudflareClient.createAccessPolicy(appId, ownerEmail)
                                                .flatMap(policyId ->
                                                        vmRepository.save(vm.withCloudflareIds(dnsRecordId, appId, policyId))
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
                    return Mono.just(vm);
                })
                .map(VmResponse::from);
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
        if (vm.getCfPolicyId() != null && vm.getCfAppId() != null) {
            return cloudflareClient.deleteAccessPolicy(vm.getCfAppId(), vm.getCfPolicyId())
                    .then(cloudflareClient.deleteAccessApp(vm.getCfAppId()))
                    .then(vm.getSubdomain() != null ? cloudflareClient.removeIngressRule(vm.getSubdomain()) : Mono.empty())
                    .then(vm.getCfDnsRecordId() != null ? cloudflareClient.deleteCname(vm.getCfDnsRecordId()) : Mono.empty());
        } else if (vm.getCfAppId() != null) {
            return cloudflareClient.deleteAccessApp(vm.getCfAppId())
                    .then(vm.getSubdomain() != null ? cloudflareClient.removeIngressRule(vm.getSubdomain()) : Mono.empty())
                    .then(vm.getCfDnsRecordId() != null ? cloudflareClient.deleteCname(vm.getCfDnsRecordId()) : Mono.empty());
        } else if (vm.getSubdomain() != null) {
            return cloudflareClient.removeIngressRule(vm.getSubdomain())
                    .then(vm.getCfDnsRecordId() != null ? cloudflareClient.deleteCname(vm.getCfDnsRecordId()) : Mono.empty());
        }
        return Mono.empty();
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
                    };
                })
                .map(VmResponse::from);
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
}
