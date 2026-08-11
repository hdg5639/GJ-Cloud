package gj.cloud.vm.application.vm.service.impl;

import gj.cloud.vm.application.port.service.PortService;
import gj.cloud.vm.application.ssh.client.OpsServiceClient;
import gj.cloud.vm.application.ssh.client.UserServiceClient;
import gj.cloud.vm.application.ssh.dto.SshKeyInternalResponse;
import gj.cloud.vm.application.vm.dto.VmAvailabilityResponse;
import gj.cloud.vm.application.vm.dto.VmAvailabilityResponse.PlanAvailability;
import gj.cloud.vm.application.vm.dto.AdminVmPageResponse;
import gj.cloud.vm.application.vm.dto.VmCreateRequest;
import gj.cloud.vm.application.vm.dto.VmPlanUpdateRequest;
import gj.cloud.vm.application.vm.dto.VmPowerRequest;
import gj.cloud.vm.application.vm.dto.VmResponse;
import gj.cloud.vm.application.vm.dto.VmStatusEvent;
import gj.cloud.vm.application.vm.dto.VmMetricsCurrentResponse;
import gj.cloud.vm.application.vm.dto.VmMetricsHistoryResponse;
import gj.cloud.vm.application.vm.service.VmAccessService;
import gj.cloud.vm.application.vm.service.VmService;
import gj.cloud.vm.domain.port.entity.VmSshAccessEmailEntity;
import gj.cloud.vm.domain.port.repository.VmSshAccessEmailRepository;
import gj.cloud.vm.domain.vm.entity.VmEntity;
import gj.cloud.vm.domain.vm.enums.PlanType;
import gj.cloud.vm.domain.vm.enums.VmStatus;
import gj.cloud.vm.domain.vm.repository.VmRepository;
import gj.cloud.vm.global.exception.VmException;
import gj.cloud.vm.global.exception.enums.VmErrorCode;
import gj.cloud.vm.global.cache.MetricsCache;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class VmServiceImpl implements VmService {

    private static final int SSH_EMAIL_MAX_COUNT = 10;

    private final VmRepository vmRepository;
    private final ProxmoxClient proxmoxClient;
    private final CloudflareClient cloudflareClient;
    private final UserServiceClient userServiceClient;
    private final OpsServiceClient opsServiceClient;
    private final SseEmitterManager sseEmitterManager;
    private final ProxmoxProperties proxmoxProperties;
    private final VmSshAccessEmailRepository sshAccessEmailRepository;
    private final PortService portService;
    private final MetricsCache metricsCache;
    private final VmAccessService vmAccessService;

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
                                opsServiceClient.issueManagementKey(creating.getId()),
                                allocateVmId()
                        )
                        .flatMap(tuple -> {
                            SshKeyInternalResponse userKey = tuple.getT1();
                            List<String> sshPublicKeys = List.of(userKey.publicKey(), tuple.getT2().publicKey());
                            int newVmid = tuple.getT3();
                            return proxmoxClient.ensurePoolExists(proxmoxProperties.getPool())
                                    .then(proxmoxClient.cloneVm(newVmid, creating.getPlanType(), creating.getName()))
                                    .flatMap(taskId -> vmRepository.save(creating.withVmidAndTaskId(newVmid, taskId)))
                                    .map(saved -> new ProvisioningContext(
                                            saved, sshPublicKeys,
                                            userKey.publicKey(), userKey.fingerprint()));
                        })
                )
                .flatMap(context -> {
                    VmEntity cloned = context.vm();
                    return updateStatus(cloned, VmStatus.BOOTING)
                            .flatMap(booting ->
                                    proxmoxClient.waitForTaskCompletion(booting.getProxmoxTaskId())
                                            .then(proxmoxClient.configureVm(
                                                    booting.getVmid(), booting.getPlanType(),
                                                    context.sshPublicKeys()))
                                            .then(proxmoxClient.resizeDisk(booting.getVmid(), booting.getDiskSizeGb()))
                                            .then(proxmoxClient.startVm(booting.getVmid()))
                                            .then(proxmoxClient.waitForIpAssignment(booting.getVmid()))
                                            .flatMap(ip -> opsServiceClient.waitForSshReadiness(
                                                            booting.getId(), ip,
                                                            context.userPublicKey(), context.userFingerprint())
                                                    .thenReturn(ip))
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

    private record ProvisioningContext(
            VmEntity vm,
            List<String> sshPublicKeys,
            String userPublicKey,
            String userFingerprint
    ) {
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
    public Mono<VmResponse> getVm(String userId, String email, UUID vmId) {
        return vmRepository.findById(vmId)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND)))
                .flatMap(vm -> {
                    if (vm.getDeletedAt() != null) {
                        return Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND));
                    }
                    return vmAccessService.checkVmReadAccess(vm.getId(), vm.getUserId(), userId, email)
                            .then(Mono.defer(() -> {
                                if (vm.getStatus() != VmStatus.RUNNING || vm.getVmid() == null) {
                                    return Mono.just(VmResponse.from(vm, null));
                                }
                                return proxmoxClient.needsReboot(vm.getVmid())
                                        .map(needsReboot -> VmResponse.from(vm, needsReboot));
                            }));
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
                    // Ops 관리 키 폐기는 강결합 금지: 실패해도 VM 삭제는 계속 진행 (best-effort)
                    Mono<Void> opsKeyRevoke = opsServiceClient.revokeManagementKey(vm.getId());
                    Mono<Void> cloudflareTeardown = teardownCloudflare(vm);
                    if (vm.getVmid() == null) {
                        return opsKeyRevoke.then(cloudflareTeardown)
                                .then(vmRepository.save(vm.withDeleted()))
                                .doOnNext(saved -> publishEvent(saved, null))
                                .then();
                    }
                    return opsKeyRevoke.then(cloudflareTeardown)
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
    public Mono<VmResponse> changePower(String userId, String email, UUID vmId, VmPowerRequest request) {
        return vmRepository.findById(vmId)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND)))
                .flatMap(vm -> {
                    if (vm.getDeletedAt() != null) return Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND));
                    if (vm.getVmid() == null) return Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND));
                    return vmAccessService.checkVmAdminAccess(vm.getId(), vm.getUserId(), userId, email).thenReturn(vm);
                })
                .flatMap(vm -> {
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

    @Override
    public Mono<List<String>> getSshAccessEmails(String userId, String email, UUID vmId) {
        return vmRepository.findById(vmId)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND)))
                .filter(vm -> vm.getDeletedAt() == null)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND)))
                .flatMap(vm -> vmAccessService.checkVmReadAccess(vm.getId(), vm.getUserId(), userId, email))
                .then(sshAccessEmailRepository.findAllByVmId(vmId)
                        .map(VmSshAccessEmailEntity::getEmail)
                        .collectList()
                );
    }

    @Override
    public Mono<List<String>> addSshAccessEmail(String userId, String ownerEmail, UUID vmId, String email) {
        return vmRepository.findById(vmId)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND)))
                .filter(vm -> vm.getDeletedAt() == null)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND)))
                .flatMap(vm -> vmAccessService.checkVmAdminAccess(vm.getId(), vm.getUserId(), userId, ownerEmail).thenReturn(vm))
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
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND)))
                .filter(vm -> vm.getDeletedAt() == null)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND)))
                .flatMap(vm -> vmAccessService.checkVmAdminAccess(vm.getId(), vm.getUserId(), userId, ownerEmail).thenReturn(vm))
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
    public Flux<VmResponse> getAllVms() {
        return vmRepository.findAllNotDeleted()
                .map(VmResponse::from);
    }

    @Override
    public Mono<AdminVmPageResponse> getAllVmsPage(int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(1, Math.min(size, 100));
        long offset = (long) (safePage - 1) * safeSize;
        return Mono.zip(
                vmRepository.findAdminPage(safeSize, offset).map(VmResponse::from).collectList(),
                vmRepository.countNotDeleted()
        ).map(tuple -> {
            List<VmResponse> content = tuple.getT1();
            long totalElements = tuple.getT2();
            int totalPages = (int) ((totalElements + safeSize - 1) / safeSize);
            return new AdminVmPageResponse(
                    content, totalPages, totalElements, safePage - 1, safeSize, content.isEmpty());
        });
    }

    @Override
    public Mono<VmResponse> getVmAdmin(UUID vmId) {
        return vmRepository.findById(vmId)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND)))
                .flatMap(vm -> {
                    if (vm.getDeletedAt() != null) return Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND));
                    return Mono.just(VmResponse.from(vm));
                });
    }

    // OBS-001: 관리자가 다른 사용자의 VM을 강제 삭제하는 고위험 조치 — 구조화 로그 라인으로 추적성 확보
    // (DB 감사로그는 Auth에만 있음, vm은 로그 라인만).
    @Override
    public Mono<Void> forceDeleteVm(UUID vmId) {
        return vmRepository.findById(vmId)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND)))
                .flatMap(vm -> {
                    if (vm.getDeletedAt() != null) return Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND));
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
                                log.error("Admin 강제 삭제 - Proxmox VM 삭제 실패, 수동 확인 필요: vmid={}, error={}", vm.getVmid(), e.getMessage());
                                return Mono.error(new VmException(VmErrorCode.PROXMOX_DELETE_FAILED));
                            })
                            .then();
                })
                .doOnSuccess(v -> log.info("AUDIT action=VM_FORCE_DELETED targetType=VM targetId={} result=SUCCESS", vmId));
    }

    // 사용자 세션 토큰이 없는 내부 호출 경로라 Ops 키 폐기를 여기서 요청하지 않음.
    // Ops의 orphan-key cleanup 스케줄러가 VM 부재를 감지해 주기적으로 정리함.
    @Override
    public Mono<Void> deleteVmInternal(UUID vmId) {
        return vmRepository.findById(vmId)
                .filter(vm -> vm.getDeletedAt() == null)
                .flatMap(vm -> updateStatus(vm, VmStatus.DELETING))
                .flatMap(vm -> {
                    Mono<Void> teardown = teardownCloudflare(vm);
                    if (vm.getVmid() == null) {
                        return teardown.then(vmRepository.save(vm.withDeleted())).then();
                    }
                    return teardown
                            .then(proxmoxClient.deleteVm(vm.getVmid()))
                            .then(vmRepository.save(vm.withDeleted()))
                            .onErrorResume(e -> {
                                log.warn("탈퇴 처리 VM 삭제 실패 (무시): vmId={}, error={}", vmId, e.getMessage());
                                return Mono.empty();
                            })
                            .then();
                })
                .then();
    }

    @Override
    public Mono<VmAvailabilityResponse> getAvailability() {
        int freeTotal = PlanType.FREE.getMaxVmCount();
        int proTotal = PlanType.PRO.getMaxVmCount();

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

    @Override
    public Mono<VmMetricsCurrentResponse> getVmMetricsCurrent(String userId, String email, UUID vmId) {
        return vmRepository.findById(vmId)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND)))
                .filter(vm -> vm.getDeletedAt() == null)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND)))
                .flatMap(vm -> vmAccessService.checkVmReadAccess(vm.getId(), vm.getUserId(), userId, email).thenReturn(vm))
                .flatMap(vm -> {
                    if (vm.getVmid() == null || !vm.getStatus().equals(VmStatus.RUNNING)) {
                        return Mono.error(new VmException(VmErrorCode.VM_NOT_RUNNING));
                    }
                    String cacheKey = "metrics:current:" + vm.getVmid();
                    var cachedData = metricsCache.get(cacheKey);
                    if (cachedData != null) {
                        return Mono.just(buildMetricsCurrentResponse(vmId, cachedData, null));
                    }

                    return proxmoxClient.getVmMetricsCurrent(vm.getVmid())
                            .zipWith(proxmoxClient.getVmDiskInfo(vm.getVmid()))
                            .doOnNext(tuple -> metricsCache.put(cacheKey, tuple.getT1()))
                            .map(tuple -> buildMetricsCurrentResponse(vmId, tuple.getT1(), tuple.getT2()));
                });
    }

    private VmMetricsCurrentResponse buildMetricsCurrentResponse(UUID vmId, com.fasterxml.jackson.databind.JsonNode currentData, gj.cloud.vm.infra.proxmox.record.GuestAgentDiskInfo diskInfo) {
        long diskUsedBytes = 0;
        long diskAllocatedBytes = currentData.path("maxdisk").asLong(0);

        if (diskInfo != null && !diskInfo.disks().isEmpty()) {
            diskUsedBytes = diskInfo.disks().get(0).used() != null
                    ? diskInfo.disks().get(0).used()
                    : 0;
        }

        return new VmMetricsCurrentResponse(
                vmId.toString(),
                currentData.path("status").asText(),
                currentData.path("cpu").decimalValue(),
                currentData.path("cpus").asInt(),
                currentData.path("mem").asLong(),
                currentData.path("maxmem").asLong(),
                diskUsedBytes,
                diskAllocatedBytes,
                currentData.path("netin").asLong(),
                currentData.path("netout").asLong(),
                currentData.path("uptime").asLong(),
                System.currentTimeMillis() / 1000
        );
    }

    @Override
    public Mono<VmMetricsHistoryResponse> getVmMetricsHistory(String userId, String email, UUID vmId, String timeframe) {
        return vmRepository.findById(vmId)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND)))
                .filter(vm -> vm.getDeletedAt() == null)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND)))
                .flatMap(vm -> vmAccessService.checkVmReadAccess(vm.getId(), vm.getUserId(), userId, email).thenReturn(vm))
                .flatMap(vm -> {
                    if (vm.getVmid() == null) {
                        return Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND));
                    }
                    return proxmoxClient.getVmMetricsHistory(vm.getVmid(), timeframe)
                            .map(rrdArray -> {
                                var dataPoints = new java.util.ArrayList<VmMetricsHistoryResponse.MetricDataPoint>();
                                if (rrdArray.isArray()) {
                                    for (com.fasterxml.jackson.databind.JsonNode row : rrdArray) {
                                        try {
                                            if (row.isArray() && row.size() >= 7) {
                                                dataPoints.add(new VmMetricsHistoryResponse.MetricDataPoint(
                                                        row.get(0).asLong(),
                                                        row.get(1).asDouble(),
                                                        row.get(3).asLong(),
                                                        row.get(5).asLong(),
                                                        row.get(6).asLong(),
                                                        0L,
                                                        0L
                                                ));
                                            }
                                        } catch (Exception e) {
                                            log.debug("RRD 데이터 포인트 파싱 실패: {}", e.getMessage());
                                        }
                                    }
                                }
                                return new VmMetricsHistoryResponse(vmId.toString(), timeframe, dataPoints);
                            });
                });
    }
}
