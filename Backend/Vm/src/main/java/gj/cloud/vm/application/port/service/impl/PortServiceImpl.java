package gj.cloud.vm.application.port.service.impl;

import gj.cloud.vm.application.port.dto.DeploymentRouteItem;
import gj.cloud.vm.application.port.dto.PortAccessAddRequest;
import gj.cloud.vm.application.port.dto.PortAddRequest;
import gj.cloud.vm.application.port.dto.PortResponse;
import gj.cloud.vm.application.port.dto.SubdomainCheckResponse;
import gj.cloud.vm.application.port.service.PortService;
import gj.cloud.vm.application.vm.service.VmAccessService;
import gj.cloud.vm.domain.port.entity.VmPortAccessEmailEntity;
import gj.cloud.vm.domain.port.entity.VmPortEntity;
import gj.cloud.vm.domain.port.enums.Protocol;
import gj.cloud.vm.domain.port.enums.Visibility;
import gj.cloud.vm.domain.port.repository.VmPortAccessEmailRepository;
import gj.cloud.vm.domain.port.repository.VmPortRepository;
import gj.cloud.vm.domain.vm.entity.VmEntity;
import gj.cloud.vm.domain.vm.repository.VmRepository;
import gj.cloud.vm.global.exception.VmException;
import gj.cloud.vm.global.exception.enums.VmErrorCode;
import gj.cloud.vm.application.ssh.client.UserServiceClient;
import gj.cloud.vm.infra.cloudflare.client.CloudflareClient;
import gj.cloud.vm.infra.cloudflare.config.CloudflareProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortServiceImpl implements PortService {

    private static final int PORT_MAX_COUNT = 5;
    private static final int EMAIL_MAX_COUNT = 10;

    private final VmRepository vmRepository;
    private final VmPortRepository vmPortRepository;
    private final VmPortAccessEmailRepository portAccessEmailRepository;
    private final CloudflareClient cloudflareClient;
    private final CloudflareProperties cloudflareProperties;
    private final UserServiceClient userServiceClient;
    private final VmAccessService vmAccessService;

    @Override
    public Mono<PortResponse> addPort(String userId, String ownerEmail, UUID vmId, PortAddRequest request, String bearerToken) {
        return vmRepository.findById(vmId)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND)))
                .filter(vm -> vm.getDeletedAt() == null)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND)))
                .flatMap(vm -> vmAccessService.checkVmAdminAccess(vmId, vm.getUserId(), userId, ownerEmail).thenReturn(vm))
                .flatMap(vm -> {
                    Mono<String> subdomainMono;
                    if (request.customSubdomain() != null && !request.customSubdomain().isBlank()) {
                        subdomainMono = validateCustomSubdomain(bearerToken, request.customSubdomain())
                                .thenReturn(request.customSubdomain());
                    } else {
                        subdomainMono = Mono.just(vm.getSubdomain() + "-" + request.nickname());
                    }
                    return subdomainMono.flatMap(portSubdomain ->
                            vmPortRepository.countByVmId(vmId)
                                    .flatMap(count -> {
                                        if (count >= PORT_MAX_COUNT) {
                                            return Mono.error(new VmException(VmErrorCode.PORT_LIMIT_EXCEEDED));
                                        }
                                        return vmPortRepository.countByVmIdAndPort(vmId, request.port());
                                    })
                                    .flatMap(existing -> {
                                        if (existing > 0) {
                                            return Mono.error(new VmException(VmErrorCode.PORT_ALREADY_EXISTS));
                                        }
                                        return vmPortRepository.countByVmIdAndNickname(vmId, request.nickname());
                                    })
                                    .flatMap(nickExists -> {
                                        if (nickExists > 0) {
                                            return Mono.error(new VmException(VmErrorCode.PORT_NICKNAME_ALREADY_EXISTS));
                                        }
                                        return Mono.just(vm);
                                    })
                                    .flatMap(v -> provisionManualPort(
                                            v, portSubdomain, request, ownerEmail)
                                            .onErrorResume(e -> {
                                                log.error("포트 Cloudflare 설정 실패: vmId={}, port={}, error={}",
                                                        vmId, request.port(), e.getMessage());
                                                return Mono.error(e);
                                            })
                                    )
                    );
                });
    }

    private Mono<Void> validateCustomSubdomain(String bearerToken, String subdomain) {
        boolean reserved = cloudflareProperties.getReservedSubdomains().stream()
                .anyMatch(r -> subdomain.equals(r) || subdomain.startsWith(r + "-"));
        if (reserved) {
            return Mono.error(new VmException(VmErrorCode.SUBDOMAIN_RESERVED));
        }
        return userServiceClient.getUserPlan(bearerToken)
                .flatMap(plan -> {
                    if (!"PRO".equals(plan)) {
                        return Mono.error(new VmException(VmErrorCode.CUSTOM_SUBDOMAIN_PRO_ONLY));
                    }
                    return vmPortRepository.countBySubdomain(subdomain);
                })
                .flatMap(count -> {
                    if (count > 0) {
                        return Mono.error(new VmException(VmErrorCode.SUBDOMAIN_ALREADY_TAKEN));
                    }
                    return Mono.empty();
                });
    }

    private Mono<Void> validateCustomSubdomainForUser(String userId, String subdomain) {
        boolean reserved = cloudflareProperties.getReservedSubdomains().stream()
                .anyMatch(r -> subdomain.equals(r) || subdomain.startsWith(r + "-"));
        if (reserved) {
            return Mono.error(new VmException(VmErrorCode.SUBDOMAIN_RESERVED));
        }
        return userServiceClient.getUserPlanById(userId)
                .flatMap(plan -> {
                    if (!"PRO".equals(plan)) {
                        return Mono.error(new VmException(VmErrorCode.CUSTOM_SUBDOMAIN_PRO_ONLY));
                    }
                    return vmPortRepository.countBySubdomain(subdomain);
                })
                .flatMap(count -> count > 0
                        ? Mono.error(new VmException(VmErrorCode.SUBDOMAIN_ALREADY_TAKEN))
                        : Mono.empty());
    }

    @Override
    public Mono<SubdomainCheckResponse> checkSubdomainAvailable(String bearerToken, String subdomain) {
        boolean reserved = cloudflareProperties.getReservedSubdomains().stream()
                .anyMatch(r -> subdomain.equals(r) || subdomain.startsWith(r + "-"));
        if (reserved) {
            return Mono.just(SubdomainCheckResponse.denied("reserved"));
        }
        return userServiceClient.getUserPlan(bearerToken)
                .flatMap(plan -> {
                    if (!"PRO".equals(plan)) {
                        return Mono.just(SubdomainCheckResponse.denied("pro-only"));
                    }
                    return vmPortRepository.countBySubdomain(subdomain)
                            .map(count -> count == 0
                                    ? SubdomainCheckResponse.ok()
                                    : SubdomainCheckResponse.denied("taken"));
                });
    }

    private Mono<PortResponse> provisionManualPort(
            VmEntity vm,
            String subdomain,
            PortAddRequest request,
            String ownerEmail
    ) {
        List<String> emails = (request.initialEmails() != null && !request.initialEmails().isEmpty())
                ? request.initialEmails()
                : List.of(ownerEmail);
        List<String> accessEmails = request.visibility() == Visibility.PRIVATE ? emails : List.of();
        return provisionCloudflarePort(
                subdomain,
                vm.getInternalIp(),
                request.port(),
                request.protocol(),
                request.visibility(),
                accessEmails,
                state -> {
                    VmPortEntity port = request.visibility() == Visibility.PUBLIC
                            ? VmPortEntity.createPublic(
                                    vm.getId(), request.port(), request.protocol(), request.nickname(),
                                    subdomain, state.dnsRecordId)
                            : VmPortEntity.createPrivate(
                                    vm.getId(), request.port(), request.protocol(), request.nickname(),
                                    subdomain, state.dnsRecordId, state.accessAppId, state.accessPolicyId);
                    return vmPortRepository.save(port)
                            .doOnNext(saved -> state.savedPortId = saved.getId())
                            .flatMap(saved -> savePortEmails(saved.getId(), accessEmails)
                                    .thenReturn(PortResponse.of(
                                            saved, accessEmails, cloudflareProperties.getBaseDomain())));
                });
    }

    private Mono<Void> savePortEmails(UUID portId, List<String> emails) {
        return Flux.fromIterable(emails)
                .map(email -> VmPortAccessEmailEntity.create(portId, email))
                .flatMap(portAccessEmailRepository::save)
                .then();
    }

    @Override
    public Flux<PortResponse> getPorts(String userId, String email, UUID vmId) {
        return vmRepository.findById(vmId)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND)))
                .filter(vm -> vm.getDeletedAt() == null)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND)))
                .flatMap(vm -> vmAccessService.checkVmReadAccess(vmId, vm.getUserId(), userId, email).thenReturn(vm))
                .flatMapMany(vm -> vmPortRepository.findAllByVmId(vmId))
                .flatMap(port -> portAccessEmailRepository.findAllByVmPortId(port.getId())
                        .map(VmPortAccessEmailEntity::getEmail)
                        .collectList()
                        .map(emails -> PortResponse.of(port, emails, cloudflareProperties.getBaseDomain()))
                );
    }

    @Override
    public Mono<Void> deletePort(String userId, String userEmail, UUID vmId, UUID portId) {
        return vmRepository.findById(vmId)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND)))
                .filter(vm -> vm.getDeletedAt() == null)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND)))
                .flatMap(vm -> vmAccessService.checkVmAdminAccess(vmId, vm.getUserId(), userId, userEmail))
                .then(vmPortRepository.findById(portId))
                .filter(port -> port.getVmId().equals(vmId))
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.PORT_NOT_FOUND)))
                .flatMap(port -> teardownPortCloudflare(port)
                        .then(portAccessEmailRepository.deleteAllByVmPortId(port.getId()))
                        .then(vmPortRepository.delete(port))
                );
    }

    @Override
    public Mono<Void> linkManualPortToDeploymentTarget(
            String requesterId,
            String requesterEmail,
            UUID vmId,
            UUID portId,
            String deploymentTargetId
    ) {
        return findManualPortForLink(requesterId, requesterEmail, vmId, portId)
                .flatMap(port -> {
                    String current = port.getLinkedDeploymentTargetId();
                    if (current != null && !current.equals(deploymentTargetId)) {
                        return Mono.error(new VmException(VmErrorCode.PORT_DEPLOYMENT_LINK_CONFLICT));
                    }
                    return vmPortRepository.save(port.withLinkedDeploymentTarget(deploymentTargetId)).then();
                });
    }

    @Override
    public Mono<Void> unlinkManualPortFromDeploymentTarget(
            String requesterId,
            String requesterEmail,
            UUID vmId,
            UUID portId,
            String deploymentTargetId
    ) {
        return findManualPortForLink(requesterId, requesterEmail, vmId, portId)
                .flatMap(port -> {
                    String current = port.getLinkedDeploymentTargetId();
                    if (current == null) return Mono.empty();
                    if (!current.equals(deploymentTargetId)) {
                        return Mono.error(new VmException(VmErrorCode.PORT_DEPLOYMENT_LINK_CONFLICT));
                    }
                    return vmPortRepository.save(port.withLinkedDeploymentTarget(null)).then();
                });
    }

    @Override
    public Mono<Void> unlinkAllManualPortsFromDeploymentTarget(
            String requesterId,
            String requesterEmail,
            UUID vmId,
            String deploymentTargetId
    ) {
        return vmRepository.findById(vmId)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND)))
                .filter(vm -> vm.getDeletedAt() == null)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND)))
                .flatMap(vm -> vmAccessService.checkVmAdminAccess(
                        vmId, vm.getUserId(), requesterId, requesterEmail))
                .thenMany(vmPortRepository.findAllByVmIdAndLinkedDeploymentTargetId(vmId, deploymentTargetId))
                .concatMap(port -> vmPortRepository.save(port.withLinkedDeploymentTarget(null)))
                .then();
    }

    private Mono<VmPortEntity> findManualPortForLink(
            String requesterId,
            String requesterEmail,
            UUID vmId,
            UUID portId
    ) {
        return vmRepository.findById(vmId)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND)))
                .filter(vm -> vm.getDeletedAt() == null)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND)))
                .flatMap(vm -> vmAccessService.checkVmAdminAccess(
                        vmId, vm.getUserId(), requesterId, requesterEmail))
                .then(vmPortRepository.findById(portId))
                .filter(port -> port.getVmId().equals(vmId))
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.PORT_NOT_FOUND)))
                .flatMap(port -> port.getDeploymentAppId() == null
                        ? Mono.just(port)
                        : Mono.error(new VmException(VmErrorCode.PORT_DEPLOYMENT_MANAGED)));
    }

    @Override
    public Mono<PortResponse> addPortAccessEmail(String userId, String userEmail, UUID vmId, UUID portId,
                                                  PortAccessAddRequest request) {
        return vmRepository.findById(vmId)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND)))
                .filter(vm -> vm.getDeletedAt() == null)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND)))
                .flatMap(vm -> vmAccessService.checkVmAdminAccess(vmId, vm.getUserId(), userId, userEmail).thenReturn(vm))
                .then(vmPortRepository.findById(portId))
                .filter(port -> port.getVmId().equals(vmId) && port.getVisibility() == Visibility.PRIVATE)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.PORT_NOT_FOUND)))
                .flatMap(port -> portAccessEmailRepository.countByVmPortId(portId)
                        .flatMap(count -> {
                            if (count >= EMAIL_MAX_COUNT) {
                                return Mono.error(new VmException(VmErrorCode.EMAIL_LIMIT_EXCEEDED));
                            }
                            return portAccessEmailRepository.save(
                                    VmPortAccessEmailEntity.create(portId, request.email()));
                        })
                        .then(portAccessEmailRepository.findAllByVmPortId(portId)
                                .map(VmPortAccessEmailEntity::getEmail)
                                .collectList()
                        )
                        .flatMap(emails -> cloudflareClient.updateAccessPolicy(
                                port.getCfAppId(), port.getCfPolicyId(), emails)
                                .thenReturn(PortResponse.of(port, emails, cloudflareProperties.getBaseDomain()))
                        )
                );
    }

    @Override
    public Mono<PortResponse> removePortAccessEmail(String userId, String userEmail, UUID vmId, UUID portId, String targetEmail) {
        return vmRepository.findById(vmId)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND)))
                .filter(vm -> vm.getDeletedAt() == null)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND)))
                .flatMap(vm -> vmAccessService.checkVmAdminAccess(vmId, vm.getUserId(), userId, userEmail).thenReturn(vm))
                .then(vmPortRepository.findById(portId))
                .filter(port -> port.getVmId().equals(vmId) && port.getVisibility() == Visibility.PRIVATE)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.PORT_NOT_FOUND)))
                .flatMap(port -> portAccessEmailRepository.findByVmPortIdAndEmail(portId, targetEmail)
                        .switchIfEmpty(Mono.error(new VmException(VmErrorCode.PORT_NOT_FOUND)))
                        .flatMap(entry -> portAccessEmailRepository.delete(entry))
                        .then(portAccessEmailRepository.findAllByVmPortId(portId)
                                .map(VmPortAccessEmailEntity::getEmail)
                                .collectList()
                        )
                        .flatMap(remaining -> cloudflareClient.updateAccessPolicy(
                                port.getCfAppId(), port.getCfPolicyId(), remaining)
                                .thenReturn(PortResponse.of(port, remaining, cloudflareProperties.getBaseDomain()))
                        )
                );
    }

    @Override
    public Mono<Void> teardownAllPortsForVm(UUID vmId) {
        return vmPortRepository.findAllByVmId(vmId)
                .flatMap(port -> teardownPortCloudflare(port)
                        .then(portAccessEmailRepository.deleteAllByVmPortId(port.getId()))
                        .then(vmPortRepository.delete(port))
                        .onErrorResume(e -> {
                            log.warn("포트 리소스 정리 실패 (무시): portId={}, error={}",
                                    port.getId(), e.getMessage());
                            return Mono.empty();
                        })
                )
                .then();
    }

    // 1.5절 규칙1 — Ops는 exposedRoutes만 넘기고, 실제 Cloudflare 조작·중복검사·포트 제한은 여기서 그대로 담당함.
    // 현재 배포가 원하는 route 집합으로 동기화: 배포가 만든 포트(deployment_id IS NOT NULL)만
    // add/remove 대상으로 삼는다. 수동 포트는 삭제·수정하지 않고, 해당 대상에 연결된 구성이 일치할 때만 재사용한다.
    @Override
    public Mono<Void> syncDeploymentRoutes(String requesterId, String requesterEmail, UUID vmId,
                                            String deploymentAppId, String deploymentId,
                                            List<DeploymentRouteItem> routes, String bearerToken) {
        return vmRepository.findById(vmId)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND)))
                .filter(vm -> vm.getDeletedAt() == null)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND)))
                .flatMap(vm -> vmAccessService.checkVmAdminAccess(vmId, vm.getUserId(), requesterId, requesterEmail).thenReturn(vm))
                .flatMap(vm -> syncDeploymentRoutesForApp(
                        vm, deploymentAppId, deploymentId, routes, requesterEmail,
                        subdomain -> validateCustomSubdomain(bearerToken, subdomain)));
    }

    @Override
    public Mono<Void> syncDeploymentRoutesAutomation(
            String requesterId,
            String requesterEmail,
            UUID vmId,
            String deploymentAppId,
            String deploymentId,
            List<DeploymentRouteItem> routes
    ) {
        return vmRepository.findById(vmId)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND)))
                .filter(vm -> vm.getDeletedAt() == null)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND)))
                .flatMap(vm -> vmAccessService.checkVmAdminAccess(
                        vmId, vm.getUserId(), requesterId, requesterEmail).thenReturn(vm))
                .flatMap(vm -> syncDeploymentRoutesForApp(
                        vm, deploymentAppId, deploymentId, routes, requesterEmail,
                        subdomain -> validateCustomSubdomainForUser(requesterId, subdomain)));
    }

    private Mono<Void> syncDeploymentRoutesForApp(
            VmEntity vm,
            String deploymentAppId,
            String deploymentId,
            List<DeploymentRouteItem> routes,
            String requesterEmail,
            Function<String, Mono<Void>> customSubdomainValidator
    ) {
        return Mono.zip(
                        vmPortRepository.findAllByVmIdAndDeploymentAppId(vm.getId(), deploymentAppId).collectList(),
                        vmPortRepository.findAllByVmIdAndLinkedDeploymentTargetId(vm.getId(), deploymentAppId)
                                .filter(port -> port.getDeploymentAppId() == null)
                                .collectList())
                        .flatMap(existing -> {
                            Map<String, VmPortEntity> existingByNickname = new HashMap<>();
                            existing.getT1().forEach(port -> existingByNickname.put(port.getNickname(), port));
                            List<VmPortEntity> linkedManualPorts = existing.getT2();
                            Map<String, DeploymentRouteItem> desiredByNickname = new HashMap<>();
                            for (DeploymentRouteItem route : routes) {
                                desiredByNickname.put(route.nickname(), route);
                            }

                            List<VmPortEntity> toRemove = existingByNickname.values().stream()
                                    .filter(port -> {
                                        DeploymentRouteItem desired = desiredByNickname.get(port.getNickname());
                                        return desired == null || !matchesRoute(vm, port, desired);
                                    })
                                    .toList();
                            List<DeploymentRouteItem> toAdd = routes.stream()
                                    .filter(route -> {
                                        VmPortEntity managed = existingByNickname.get(route.nickname());
                                        if (managed != null && matchesRoute(vm, managed, route)) {
                                            return false;
                                        }
                                        boolean linkedManualRoute = linkedManualPorts.stream()
                                                .anyMatch(port -> matchesRoute(vm, port, route));
                                        if (linkedManualRoute) {
                                            log.info("연결된 수동 CNAME을 배포 라우트로 재사용: vmId={}, targetId={}, nickname={}",
                                                    vm.getId(), deploymentAppId, route.nickname());
                                        }
                                        return !linkedManualRoute;
                                    })
                                    .toList();

                            Mono<Void> removeMono = Flux.fromIterable(toRemove)
                                    .concatMap(port -> teardownPortCloudflare(port)
                                            .then(portAccessEmailRepository.deleteAllByVmPortId(port.getId()))
                                            .then(vmPortRepository.delete(port))
                                            .onErrorResume(e -> {
                                                log.warn("배포 라우트 정리 실패(무시): portId={}, error={}", port.getId(), e.getMessage());
                                                return Mono.empty();
                                            })
                                    )
                                    .then();

                            Mono<Void> addMono = Flux.fromIterable(toAdd)
                                    .concatMap(route -> addDeploymentRoute(
                                            vm, deploymentAppId, deploymentId, route,
                                            requesterEmail, customSubdomainValidator))
                                    .then();

                            return removeMono.then(addMono);
                        });
    }

    private Mono<Void> addDeploymentRoute(
            VmEntity vm,
            String deploymentAppId,
            String deploymentId,
            DeploymentRouteItem route,
            String requesterEmail,
            Function<String, Mono<Void>> customSubdomainValidator
    ) {
        Protocol protocol;
        Visibility visibility;
        try {
            protocol = Protocol.valueOf(route.protocol());
            visibility = Visibility.valueOf(route.visibility());
        } catch (IllegalArgumentException e) {
            return Mono.error(new VmException(VmErrorCode.INVALID_ROUTE_SPEC));
        }

        Mono<String> subdomainMono = (route.customSubdomain() != null && !route.customSubdomain().isBlank())
                ? customSubdomainValidator.apply(route.customSubdomain()).thenReturn(route.customSubdomain())
                : Mono.just(vm.getSubdomain() + "-" + route.nickname());

        return subdomainMono.flatMap(subdomain -> addDeploymentRouteWithSubdomain(
                vm, deploymentAppId, deploymentId, route, requesterEmail, protocol, visibility, subdomain));
    }

    private Mono<Void> addDeploymentRouteWithSubdomain(
            VmEntity vm,
            String deploymentAppId,
            String deploymentId,
            DeploymentRouteItem route,
            String requesterEmail,
            Protocol protocol,
            Visibility visibility,
            String subdomain
    ) {
        return vmPortRepository.countByVmId(vm.getId())
                .flatMap(count -> {
                    if (count >= PORT_MAX_COUNT) {
                        return Mono.error(new VmException(VmErrorCode.PORT_LIMIT_EXCEEDED));
                    }
                    // 같은 배포의 도메인 라우트들은 하나의 Caddy router 포트를 공유할 수 있으므로,
                    // 수동 포트나 다른 배포가 점유한 경우에만 충돌로 처리한다.
                    return vmPortRepository.countByVmIdAndPortForOtherOwners(
                            vm.getId(), route.port(), deploymentAppId);
                })
                .flatMap(existing -> {
                    if (existing > 0) {
                        return Mono.error(new VmException(VmErrorCode.PORT_ALREADY_EXISTS));
                    }
                    return vmPortRepository.countByVmIdAndNickname(vm.getId(), route.nickname());
                })
                .flatMap(nickExists -> {
                    if (nickExists > 0) {
                        return Mono.error(new VmException(VmErrorCode.PORT_NICKNAME_ALREADY_EXISTS));
                    }
                    List<String> accessEmails = visibility == Visibility.PRIVATE
                            ? List.of(requesterEmail)
                            : List.of();
                    return provisionCloudflarePort(
                            subdomain,
                            vm.getInternalIp(),
                            route.port(),
                            protocol,
                            visibility,
                            accessEmails,
                            state -> saveDeploymentPort(
                                    state, vm.getId(), deploymentAppId, deploymentId, route,
                                    protocol, visibility, subdomain, requesterEmail));
                });
    }

    private Mono<Void> saveDeploymentPort(
            PortProvisioningState state,
            UUID vmId,
            String deploymentAppId,
            String deploymentId,
            DeploymentRouteItem route,
            Protocol protocol,
            Visibility visibility,
            String subdomain,
            String requesterEmail
    ) {
        if (visibility == Visibility.PUBLIC) {
            VmPortEntity port = VmPortEntity.createPublic(
                            vmId, route.port(), protocol, route.nickname(), subdomain, state.dnsRecordId)
                    .withDeployment(deploymentAppId, deploymentId);
            return vmPortRepository.save(port)
                    .doOnNext(saved -> state.savedPortId = saved.getId())
                    .then();
        }

        VmPortEntity port = VmPortEntity.createPrivate(
                        vmId, route.port(), protocol, route.nickname(), subdomain,
                        state.dnsRecordId, state.accessAppId, state.accessPolicyId)
                .withDeployment(deploymentAppId, deploymentId);
        return vmPortRepository.save(port)
                .doOnNext(saved -> state.savedPortId = saved.getId())
                .flatMap(saved -> portAccessEmailRepository.save(
                        VmPortAccessEmailEntity.create(saved.getId(), requesterEmail)))
                .then();
    }

    private boolean matchesRoute(VmEntity vm, VmPortEntity port, DeploymentRouteItem route) {
        String expectedSubdomain = route.customSubdomain() != null && !route.customSubdomain().isBlank()
                ? route.customSubdomain()
                : vm.getSubdomain() + "-" + route.nickname();
        return port.getPort() == route.port()
                && port.getProtocol().name().equals(route.protocol())
                && port.getVisibility().name().equals(route.visibility())
                && expectedSubdomain.equals(port.getSubdomain());
    }

    private <T> Mono<T> provisionCloudflarePort(
            String subdomain,
            String internalIp,
            int port,
            Protocol protocol,
            Visibility visibility,
            List<String> accessEmails,
            Function<PortProvisioningState, Mono<T>> persistence
    ) {
        PortProvisioningState state = new PortProvisioningState(subdomain);
        return cloudflareClient.ensureCname(subdomain)
                .doOnNext(registration -> {
                    state.dnsRecordId = registration.recordId();
                    state.dnsCreated = registration.created();
                })
                .flatMap(registration -> {
                    // 응답 유실로 성공 여부를 모르는 경우에도 remove는 멱등하므로 보상 정리 대상으로 표시한다.
                    state.ingressTouched = true;
                    return cloudflareClient.addIngressRule(subdomain, internalIp, port, protocol.name());
                })
                .then(Mono.defer(() -> {
                    if (visibility == Visibility.PUBLIC) {
                        return Mono.empty();
                    }
                    return cloudflareClient.createAccessApp(subdomain, "self_hosted")
                            .doOnNext(appId -> state.accessAppId = appId)
                            .flatMap(appId -> cloudflareClient.createAccessPolicy(appId, accessEmails))
                            .doOnNext(policyId -> state.accessPolicyId = policyId)
                            .then();
                }))
                .then(Mono.defer(() -> persistence.apply(state)))
                .onErrorResume(error -> compensateProvisioning(state)
                        .then(Mono.error(error)));
    }

    private Mono<Void> compensateProvisioning(PortProvisioningState state) {
        Mono<Void> databaseCleanup = state.savedPortId == null
                ? Mono.empty()
                : bestEffortCleanup("포트 DB", state.subdomain,
                        portAccessEmailRepository.deleteAllByVmPortId(state.savedPortId)
                                .then(vmPortRepository.deleteById(state.savedPortId)));
        Mono<Void> policyCleanup = state.accessPolicyId == null || state.accessAppId == null
                ? Mono.empty()
                : bestEffortCleanup("Access Policy", state.subdomain,
                        cloudflareClient.deleteAccessPolicy(state.accessAppId, state.accessPolicyId));
        Mono<Void> appCleanup = state.accessAppId == null
                ? Mono.empty()
                : bestEffortCleanup("Access App", state.subdomain,
                        cloudflareClient.deleteAccessApp(state.accessAppId));
        Mono<Void> ingressCleanup = !state.ingressTouched
                ? Mono.empty()
                : bestEffortCleanup("Ingress", state.subdomain,
                        cloudflareClient.removeIngressRule(state.subdomain));
        Mono<Void> cnameCleanup = state.dnsRecordId == null || !state.dnsCreated
                ? Mono.empty()
                : bestEffortCleanup("CNAME", state.subdomain,
                        cloudflareClient.deleteCname(state.dnsRecordId));
        return databaseCleanup
                .then(policyCleanup)
                .then(appCleanup)
                .then(ingressCleanup)
                .then(cnameCleanup);
    }

    private Mono<Void> bestEffortCleanup(String resource, String subdomain, Mono<Void> cleanup) {
        return cleanup.onErrorResume(error -> {
            log.warn("포트 프로비저닝 보상 정리 실패(계속 진행): resource={}, subdomain={}, error={}",
                    resource, subdomain, error.getMessage());
            return Mono.empty();
        });
    }

    private static final class PortProvisioningState {
        private final String subdomain;
        private String dnsRecordId;
        private boolean dnsCreated;
        private boolean ingressTouched;
        private String accessAppId;
        private String accessPolicyId;
        private UUID savedPortId;

        private PortProvisioningState(String subdomain) {
            this.subdomain = subdomain;
        }
    }

    private Mono<Void> teardownPortCloudflare(VmPortEntity port) {
        Mono<Void> accessCleanup = (port.getCfPolicyId() != null && port.getCfAppId() != null)
                ? cloudflareClient.deleteAccessPolicy(port.getCfAppId(), port.getCfPolicyId())
                        .then(cloudflareClient.deleteAccessApp(port.getCfAppId()))
                : (port.getCfAppId() != null
                        ? cloudflareClient.deleteAccessApp(port.getCfAppId())
                        : Mono.empty());

        return accessCleanup
                .then(cloudflareClient.removeIngressRule(port.getSubdomain()))
                .then(port.getCfDnsRecordId() != null
                        ? cloudflareClient.deleteCname(port.getCfDnsRecordId())
                        : Mono.empty());
    }
}
