package gj.cloud.vm.application.vm.service.impl;

import gj.cloud.vm.application.ssh.client.UserServiceClient;
import gj.cloud.vm.application.vm.dto.VmCreateRequest;
import gj.cloud.vm.application.vm.dto.VmResponse;
import gj.cloud.vm.application.vm.dto.VmStatusEvent;
import gj.cloud.vm.application.vm.service.VmService;
import gj.cloud.vm.domain.vm.entity.VmEntity;
import gj.cloud.vm.domain.vm.enums.VmStatus;
import gj.cloud.vm.domain.vm.repository.VmRepository;
import gj.cloud.vm.global.exception.VmException;
import gj.cloud.vm.global.exception.enums.VmErrorCode;
import gj.cloud.vm.global.sse.SseEmitterManager;
import gj.cloud.vm.infra.proxmox.client.ProxmoxClient;
import gj.cloud.vm.infra.proxmox.config.ProxmoxProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VmServiceImpl implements VmService {

    private final VmRepository vmRepository;
    private final ProxmoxClient proxmoxClient;
    private final UserServiceClient userServiceClient;
    private final SseEmitterManager sseEmitterManager;
    private final ProxmoxProperties proxmoxProperties;

    @Override
    public Mono<VmResponse> createVm(String userId, String bearerToken, VmCreateRequest request) {
        VmEntity pending = VmEntity.createPending(userId, request.name(), request.planType(), request.sshKeyId());

        return vmRepository.save(pending)
                .doOnNext(saved -> provisionVm(saved, bearerToken)
                        .subscribe(
                                null,
                                error -> log.error("프로비저닝 실패: vmId={}, error={}", saved.getId(), error.getMessage())
                        ))
                .map(VmResponse::from);
    }

    private Mono<Void> provisionVm(VmEntity vm, String bearerToken) {
        return updateStatus(vm, VmStatus.CREATING)
                .flatMap(creating ->
                        Mono.zip(
                                userServiceClient.getSshKey(bearerToken, creating.getSshKeyId()),
                                allocateVmId()
                        )
                        .flatMap(tuple -> {
                            String sshPublicKey = tuple.getT1().publicKey();
                            int newVmid = tuple.getT2();
                            return proxmoxClient.cloneVm(newVmid, creating.getPlanType(), creating.getName(), sshPublicKey)
                                    .flatMap(taskId -> vmRepository.save(creating.withVmidAndTaskId(newVmid, taskId)));
                        })
                )
                .flatMap(cloned -> updateStatus(cloned, VmStatus.BOOTING))
                .flatMap(booting ->
                        proxmoxClient.waitForTaskCompletion(booting.getProxmoxTaskId())
                                .then(proxmoxClient.startVm(booting.getVmid()))
                                .then(proxmoxClient.waitForIpAssignment(booting.getVmid()))
                                .flatMap(ip -> {
                                    VmEntity running = booting.withRunning(ip);
                                    return vmRepository.save(running)
                                            .doOnNext(saved -> publishEvent(saved, null));
                                })
                )
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
                    if (vm.getVmid() == null) {
                        // vmid 없으면 바로 DELETED 처리
                        return vmRepository.save(vm.withDeleted())
                                .doOnNext(saved -> publishEvent(saved, null))
                                .then();
                    }
                    return proxmoxClient.deleteVm(vm.getVmid())
                            .then(vmRepository.save(vm.withDeleted()))
                            .doOnNext(saved -> publishEvent(saved, null))
                            .onErrorResume(e -> {
                                log.error("Proxmox VM 삭제 실패, 수동 확인 필요: vmid={}, error={}", vm.getVmid(), e.getMessage());
                                return Mono.error(new VmException(VmErrorCode.PROXMOX_DELETE_FAILED));
                            })
                            .then();
                });
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
        return vmRepository.findAllActiveVmids()
                .collectList()
                .map(existingIds -> {
                    Set<Integer> used = new HashSet<>(existingIds);
                    for (int i = proxmoxProperties.getVmidRangeStart(); i <= proxmoxProperties.getVmidRangeEnd(); i++) {
                        if (!used.contains(i)) return i;
                    }
                    throw new VmException(VmErrorCode.VMID_ALLOCATION_FAILED);
                });
    }
}
