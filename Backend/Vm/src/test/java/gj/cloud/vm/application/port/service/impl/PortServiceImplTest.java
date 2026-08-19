package gj.cloud.vm.application.port.service.impl;

import gj.cloud.vm.application.port.dto.DeploymentRouteItem;
import gj.cloud.vm.application.ssh.client.UserServiceClient;
import gj.cloud.vm.application.vm.service.VmAccessService;
import gj.cloud.vm.domain.port.entity.VmPortEntity;
import gj.cloud.vm.domain.port.enums.Protocol;
import gj.cloud.vm.domain.port.enums.Visibility;
import gj.cloud.vm.domain.port.repository.VmPortAccessEmailRepository;
import gj.cloud.vm.domain.port.repository.VmPortRepository;
import gj.cloud.vm.domain.vm.entity.VmEntity;
import gj.cloud.vm.domain.vm.enums.PlanType;
import gj.cloud.vm.domain.vm.enums.VmStatus;
import gj.cloud.vm.domain.vm.repository.VmRepository;
import gj.cloud.vm.infra.cloudflare.client.CloudflareClient;
import gj.cloud.vm.infra.cloudflare.config.CloudflareProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PortServiceImplTest {

    private final VmRepository vmRepository = mock(VmRepository.class);
    private final VmPortRepository vmPortRepository = mock(VmPortRepository.class);
    private final VmPortAccessEmailRepository accessEmailRepository = mock(VmPortAccessEmailRepository.class);
    private final CloudflareClient cloudflareClient = mock(CloudflareClient.class);
    private final UserServiceClient userServiceClient = mock(UserServiceClient.class);
    private final VmAccessService vmAccessService = mock(VmAccessService.class);
    private final CloudflareProperties cloudflareProperties = new CloudflareProperties();
    private final PortServiceImpl service = new PortServiceImpl(
            vmRepository,
            vmPortRepository,
            accessEmailRepository,
            cloudflareClient,
            cloudflareProperties,
            userServiceClient,
            vmAccessService);

    private UUID vmId;
    private VmEntity vm;

    @BeforeEach
    void setUp() {
        vmId = UUID.randomUUID();
        vm = VmEntity.builder()
                .id(vmId)
                .userId("owner-1")
                .name("vm")
                .planType(PlanType.PRO)
                .status(VmStatus.RUNNING)
                .internalIp("192.168.0.10")
                .subdomain("gj-test")
                .build();
        cloudflareProperties.setBaseDomain("example.test");
        when(vmRepository.findById(vmId)).thenReturn(Mono.just(vm));
        when(vmAccessService.checkVmAdminAccess(vmId, "owner-1", "owner-1", "owner@example.com"))
                .thenReturn(Mono.empty());
    }

    @Test
    void linkedManualCnameSatisfiesMatchingDeploymentRoute() {
        String targetId = UUID.randomUUID().toString();
        VmPortEntity manual = VmPortEntity.createPublic(
                        vmId, 80, Protocol.HTTP, "web", "preview", "dns-manual")
                .withLinkedDeploymentTarget(targetId);
        when(vmPortRepository.findAllByVmIdAndDeploymentAppId(vmId, targetId)).thenReturn(Flux.empty());
        when(vmPortRepository.findAllByVmIdAndLinkedDeploymentTargetId(vmId, targetId))
                .thenReturn(Flux.just(manual));

        service.syncDeploymentRoutesAutomation(
                "owner-1",
                "owner@example.com",
                vmId,
                targetId,
                UUID.randomUUID().toString(),
                List.of(route("PUBLIC"))).block();

        verifyNoInteractions(cloudflareClient);
        verify(vmPortRepository, never()).save(any());
    }

    @Test
    void compensatesAllCreatedResourcesWhenPrivateRoutePersistenceFails() {
        String targetId = UUID.randomUUID().toString();
        when(vmPortRepository.findAllByVmIdAndDeploymentAppId(vmId, targetId)).thenReturn(Flux.empty());
        when(vmPortRepository.findAllByVmIdAndLinkedDeploymentTargetId(vmId, targetId)).thenReturn(Flux.empty());
        when(userServiceClient.getUserPlanById("owner-1")).thenReturn(Mono.just("PRO"));
        when(vmPortRepository.countBySubdomain("preview")).thenReturn(Mono.just(0L));
        when(vmPortRepository.countByVmId(vmId)).thenReturn(Mono.just(0L));
        when(vmPortRepository.countByVmIdAndPortForOtherOwners(vmId, 80, targetId)).thenReturn(Mono.just(0L));
        when(vmPortRepository.countByVmIdAndNickname(vmId, "web")).thenReturn(Mono.just(0L));
        when(cloudflareClient.ensureCname("preview")).thenReturn(Mono.just(
                new CloudflareClient.CnameRegistration("dns-1", true)));
        when(cloudflareClient.addIngressRule("preview", "192.168.0.10", 80, "HTTP"))
                .thenReturn(Mono.empty());
        when(cloudflareClient.createAccessApp("preview", "self_hosted"))
                .thenReturn(Mono.just("app-1"));
        when(cloudflareClient.createAccessPolicy("app-1", List.of("owner@example.com")))
                .thenReturn(Mono.just("policy-1"));
        when(vmPortRepository.save(any(VmPortEntity.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(accessEmailRepository.save(any())).thenReturn(Mono.error(new IllegalStateException("db failed")));
        when(accessEmailRepository.deleteAllByVmPortId(any())).thenReturn(Mono.empty());
        when(vmPortRepository.deleteById(any(UUID.class))).thenReturn(Mono.empty());
        when(cloudflareClient.deleteAccessPolicy("app-1", "policy-1")).thenReturn(Mono.empty());
        when(cloudflareClient.deleteAccessApp("app-1")).thenReturn(Mono.empty());
        when(cloudflareClient.removeIngressRule("preview")).thenReturn(Mono.empty());
        when(cloudflareClient.deleteCname("dns-1")).thenReturn(Mono.empty());

        assertThatThrownBy(() -> service.syncDeploymentRoutesAutomation(
                "owner-1",
                "owner@example.com",
                vmId,
                targetId,
                UUID.randomUUID().toString(),
                List.of(route("PRIVATE"))).block())
                .hasMessage("db failed");

        verify(accessEmailRepository).deleteAllByVmPortId(any(UUID.class));
        verify(vmPortRepository).deleteById(any(UUID.class));
        verify(cloudflareClient).deleteAccessPolicy("app-1", "policy-1");
        verify(cloudflareClient).deleteAccessApp("app-1");
        verify(cloudflareClient).removeIngressRule("preview");
        verify(cloudflareClient).deleteCname("dns-1");
    }

    @Test
    void preservesAdoptedCnameWhenDownstreamPersistenceFails() {
        String targetId = UUID.randomUUID().toString();
        when(vmPortRepository.findAllByVmIdAndDeploymentAppId(vmId, targetId)).thenReturn(Flux.empty());
        when(vmPortRepository.findAllByVmIdAndLinkedDeploymentTargetId(vmId, targetId)).thenReturn(Flux.empty());
        when(userServiceClient.getUserPlanById("owner-1")).thenReturn(Mono.just("PRO"));
        when(vmPortRepository.countBySubdomain("preview")).thenReturn(Mono.just(0L));
        when(vmPortRepository.countByVmId(vmId)).thenReturn(Mono.just(0L));
        when(vmPortRepository.countByVmIdAndPortForOtherOwners(vmId, 80, targetId)).thenReturn(Mono.just(0L));
        when(vmPortRepository.countByVmIdAndNickname(vmId, "web")).thenReturn(Mono.just(0L));
        when(cloudflareClient.ensureCname("preview")).thenReturn(Mono.just(
                new CloudflareClient.CnameRegistration("dns-existing", false)));
        when(cloudflareClient.addIngressRule("preview", "192.168.0.10", 80, "HTTP"))
                .thenReturn(Mono.empty());
        when(vmPortRepository.save(any(VmPortEntity.class)))
                .thenReturn(Mono.error(new IllegalStateException("db failed")));
        when(cloudflareClient.removeIngressRule("preview")).thenReturn(Mono.empty());

        assertThatThrownBy(() -> service.syncDeploymentRoutesAutomation(
                "owner-1",
                "owner@example.com",
                vmId,
                targetId,
                UUID.randomUUID().toString(),
                List.of(route("PUBLIC"))).block())
                .hasMessage("db failed");

        verify(cloudflareClient).removeIngressRule("preview");
        verify(cloudflareClient, never()).deleteCname("dns-existing");
    }

    private DeploymentRouteItem route(String visibility) {
        return new DeploymentRouteItem("web", 80, "HTTP", visibility, "web", "preview");
    }
}
