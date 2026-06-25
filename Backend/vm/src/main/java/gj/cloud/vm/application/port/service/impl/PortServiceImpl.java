package gj.cloud.vm.application.port.service.impl;

import gj.cloud.vm.application.port.dto.PortAccessAddRequest;
import gj.cloud.vm.application.port.dto.PortAddRequest;
import gj.cloud.vm.application.port.dto.PortResponse;
import gj.cloud.vm.application.port.dto.SubdomainCheckResponse;
import gj.cloud.vm.application.port.service.PortService;
import gj.cloud.vm.domain.port.entity.VmPortAccessEmailEntity;
import gj.cloud.vm.domain.port.entity.VmPortEntity;
import gj.cloud.vm.domain.port.enums.Visibility;
import gj.cloud.vm.domain.port.repository.VmPortAccessEmailRepository;
import gj.cloud.vm.domain.port.repository.VmPortRepository;
import gj.cloud.vm.domain.org.repository.OrganizationMemberRepository;
import gj.cloud.vm.domain.org.repository.OrganizationVmRepository;
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

import java.util.List;
import java.util.UUID;

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
    private final OrganizationVmRepository orgVmRepository;
    private final OrganizationMemberRepository orgMemberRepository;

    @Override
    public Mono<PortResponse> addPort(String userId, String ownerEmail, UUID vmId, PortAddRequest request, String bearerToken) {
        Mono<String> subdomainMono;

        if (request.customSubdomain() != null && !request.customSubdomain().isBlank()) {
            subdomainMono = validateCustomSubdomain(bearerToken, request.customSubdomain())
                    .thenReturn(request.customSubdomain());
        } else {
            subdomainMono = vmRepository.findById(vmId)
                    .filter(vm -> vm.getUserId().equals(userId) && vm.getDeletedAt() == null)
                    .switchIfEmpty(Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND)))
                    .map(vm -> vm.getSubdomain() + "-" + request.nickname());
        }

        return subdomainMono.flatMap(portSubdomain ->
                vmRepository.findById(vmId)
                        .filter(vm -> vm.getUserId().equals(userId) && vm.getDeletedAt() == null)
                        .switchIfEmpty(Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND)))
                        .flatMap(vm -> vmPortRepository.countByVmId(vmId)
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
                        )
                        .flatMap(vm -> cloudflareClient.registerCname(portSubdomain)
                                .flatMap(dnsRecordId ->
                                        cloudflareClient.addIngressRule(
                                                        portSubdomain, vm.getInternalIp(),
                                                        request.port(), request.protocol().name())
                                                .then(setupVisibility(vm.getId(), portSubdomain, dnsRecordId,
                                                        request, ownerEmail, request.nickname()))
                                )
                                .onErrorResume(e -> {
                                    log.error("포트 Cloudflare 설정 실패: vmId={}, port={}, error={}",
                                            vmId, request.port(), e.getMessage());
                                    return Mono.error(e);
                                })
                        )
        );
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
                .flatMap(vm -> checkVmReadAccess(vmId, vm.getUserId(), userId, email).thenReturn(vm))
                .flatMapMany(vm -> vmPortRepository.findAllByVmId(vmId))
                .flatMap(port -> portAccessEmailRepository.findAllByVmPortId(port.getId())
                        .map(VmPortAccessEmailEntity::getEmail)
                        .collectList()
                        .map(emails -> PortResponse.of(port, emails, cloudflareProperties.getBaseDomain()))
                );
    }

    private Mono<Void> checkVmReadAccess(UUID vmId, String ownerId, String requesterId, String requesterEmail) {
        if (ownerId.equals(requesterId)) return Mono.empty();
        return orgVmRepository.findAllByVmId(vmId)
                .flatMap(orgVm -> orgMemberRepository.findAcceptedByOrgIdAndEmail(orgVm.getOrganizationId(), requesterEmail))
                .next()
                .<Void>flatMap(m -> Mono.empty())
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND)));
    }

    @Override
    public Mono<Void> deletePort(String userId, UUID vmId, UUID portId) {
        return vmRepository.findById(vmId)
                .filter(vm -> vm.getUserId().equals(userId) && vm.getDeletedAt() == null)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND)))
                .then(vmPortRepository.findById(portId))
                .filter(port -> port.getVmId().equals(vmId))
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.PORT_NOT_FOUND)))
                .flatMap(port -> teardownPortCloudflare(port)
                        .then(portAccessEmailRepository.deleteAllByVmPortId(port.getId()))
                        .then(vmPortRepository.delete(port))
                );
    }

    @Override
    public Mono<PortResponse> addPortAccessEmail(String userId, UUID vmId, UUID portId,
                                                  PortAccessAddRequest request) {
        return vmRepository.findById(vmId)
                .filter(vm -> vm.getUserId().equals(userId) && vm.getDeletedAt() == null)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND)))
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
    public Mono<PortResponse> removePortAccessEmail(String userId, UUID vmId, UUID portId, String email) {
        return vmRepository.findById(vmId)
                .filter(vm -> vm.getUserId().equals(userId) && vm.getDeletedAt() == null)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND)))
                .then(vmPortRepository.findById(portId))
                .filter(port -> port.getVmId().equals(vmId) && port.getVisibility() == Visibility.PRIVATE)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.PORT_NOT_FOUND)))
                .flatMap(port -> portAccessEmailRepository.findByVmPortIdAndEmail(portId, email)
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
