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
                                    .flatMap(v -> cloudflareClient.registerCname(portSubdomain)
                                            .flatMap(dnsRecordId ->
                                                    cloudflareClient.addIngressRule(
                                                                    portSubdomain, v.getInternalIp(),
                                                                    request.port(), request.protocol().name())
                                                            .then(setupVisibility(v.getId(), portSubdomain, dnsRecordId,
                                                                    request, ownerEmail, request.nickname()))
                                            )
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

    private Mono<PortResponse> setupVisibility(UUID vmId, String subdomain, String dnsRecordId,
                                               PortAddRequest request, String ownerEmail, String nickname) {
        if (request.visibility() == Visibility.PUBLIC) {
            VmPortEntity port = VmPortEntity.createPublic(vmId, request.port(),
                    request.protocol(), nickname, subdomain, dnsRecordId);
            return vmPortRepository.save(port)
                    .map(saved -> PortResponse.of(saved, List.of(), cloudflareProperties.getBaseDomain()));
        }

        List<String> emails = (request.initialEmails() != null && !request.initialEmails().isEmpty())
                ? request.initialEmails()
                : List.of(ownerEmail);

        return cloudflareClient.createAccessApp(subdomain, "self_hosted")
                .flatMap(appId -> cloudflareClient.createAccessPolicy(appId, emails)
                        .flatMap(policyId -> {
                            VmPortEntity port = VmPortEntity.createPrivate(vmId, request.port(),
                                    request.protocol(), nickname, subdomain, dnsRecordId, appId, policyId);
                            return vmPortRepository.save(port)
                                    .flatMap(saved -> savePortEmails(saved.getId(), emails)
                                            .thenReturn(PortResponse.of(saved, emails,
                                                    cloudflareProperties.getBaseDomain())));
                        })
                );
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
    // 현재 배포가 원하는 route 집합으로 동기화: 배포가 만든 포트(deployment_id IS NOT NULL)만 add/remove 대상으로 삼고,
    // 사용자가 수동으로 추가한 포트는 절대 건드리지 않음.
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
        return vmPortRepository.findAllByVmIdAndDeploymentAppId(vm.getId(), deploymentAppId)
                        .collectMap(VmPortEntity::getNickname, p -> p)
                        .flatMap(existingByNickname -> {
                            Map<String, DeploymentRouteItem> desiredByNickname = new HashMap<>();
                            for (DeploymentRouteItem route : routes) {
                                desiredByNickname.put(route.nickname(), route);
                            }

                            List<VmPortEntity> toRemove = existingByNickname.values().stream()
                                    .filter(port -> !desiredByNickname.containsKey(port.getNickname()))
                                    .toList();
                            List<DeploymentRouteItem> toAdd = routes.stream()
                                    .filter(route -> !existingByNickname.containsKey(route.nickname()))
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
                    return vmPortRepository.countByVmIdAndPort(vm.getId(), route.port());
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
                    return cloudflareClient.registerCname(subdomain);
                })
                .flatMap(dnsRecordId -> cloudflareClient.addIngressRule(subdomain, vm.getInternalIp(), route.port(), protocol.name())
                        .then(saveDeploymentPort(vm.getId(), deploymentAppId, deploymentId, route, protocol,
                                visibility, subdomain, dnsRecordId, requesterEmail))
                );
    }

    private Mono<Void> saveDeploymentPort(
            UUID vmId,
            String deploymentAppId,
            String deploymentId,
            DeploymentRouteItem route,
            Protocol protocol,
            Visibility visibility,
            String subdomain,
            String dnsRecordId,
            String requesterEmail
    ) {
        if (visibility == Visibility.PUBLIC) {
            VmPortEntity port = VmPortEntity.createPublic(vmId, route.port(), protocol, route.nickname(), subdomain, dnsRecordId)
                    .withDeployment(deploymentAppId, deploymentId);
            return vmPortRepository.save(port).then();
        }

        return cloudflareClient.createAccessApp(subdomain, "self_hosted")
                .flatMap(appId -> cloudflareClient.createAccessPolicy(appId, List.of(requesterEmail))
                        .flatMap(policyId -> {
                            VmPortEntity port = VmPortEntity.createPrivate(vmId, route.port(), protocol, route.nickname(),
                                            subdomain, dnsRecordId, appId, policyId)
                                    .withDeployment(deploymentAppId, deploymentId);
                            return vmPortRepository.save(port)
                                    .flatMap(saved -> portAccessEmailRepository.save(
                                            VmPortAccessEmailEntity.create(saved.getId(), requesterEmail)));
                        })
                ).then();
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
