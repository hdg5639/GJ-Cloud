package gj.cloud.vm.application.port.service.impl;

import gj.cloud.vm.application.port.dto.DeploymentRouteItem;
import gj.cloud.vm.application.port.dto.PortAddRequest;
import gj.cloud.vm.global.exception.VmException;
import gj.cloud.vm.global.exception.enums.VmErrorCode;
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
import java.util.ArrayList;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void manualRoutesSharePortWithSeparateCnamesAndAccessPolicies() {
        List<VmPortEntity> stored = stubRouteStore();
        when(cloudflareClient.createAccessApp("gj-test-private", "self_hosted"))
                .thenReturn(Mono.just("private-app"));
        when(cloudflareClient.createAccessPolicy("private-app", List.of("owner@example.com")))
                .thenReturn(Mono.just("private-policy"));
        when(accessEmailRepository.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        service.addPort("owner-1", "owner@example.com", vmId,
                new PortAddRequest(8080, Protocol.HTTP, Visibility.PRIVATE, "private", List.of(), null), "token").block();
        service.addPort("owner-1", "owner@example.com", vmId,
                new PortAddRequest(8080, Protocol.HTTP, Visibility.PUBLIC, "public", List.of(), null), "token").block();

        assertThat(stored).extracting(VmPortEntity::getPort).containsExactly(8080, 8080);
        assertThat(stored).extracting(VmPortEntity::getSubdomain).containsExactly("gj-test-private", "gj-test-public");
        assertThat(stored.get(0).getCfAppId()).isEqualTo("private-app");
        assertThat(stored.get(1).getCfAppId()).isNull();
        verify(cloudflareClient).addIngressRule("gj-test-private", "192.168.0.10", 8080, "HTTP");
        verify(cloudflareClient).addIngressRule("gj-test-public", "192.168.0.10", 8080, "HTTP");
        verify(cloudflareClient, never()).createAccessApp("gj-test-public", "self_hosted");
    }

    @Test
    void deploymentRoutesSharePortWithManualAndOtherDeploymentOwners() {
        List<VmPortEntity> stored = stubRouteStore();
        VmPortEntity manual = VmPortEntity.createPublic(vmId, 8080, Protocol.HTTP, "manual", "manual", "dns-manual");
        VmPortEntity other = VmPortEntity.createPublic(vmId, 8080, Protocol.HTTP, "other", "other", "dns-other")
                .withDeployment("other-target", "other-deployment");
        stored.addAll(List.of(manual, other));
        when(vmPortRepository.findAllByVmIdAndDeploymentAppId(vmId, "new-target")).thenReturn(Flux.empty());
        when(vmPortRepository.findAllByVmIdAndLinkedDeploymentTargetId(vmId, "new-target")).thenReturn(Flux.empty());
        service.syncDeploymentRoutesAutomation("owner-1", "owner@example.com", vmId, "new-target", "new-deployment",
                List.of(new DeploymentRouteItem("one", 8080, "HTTP", "PUBLIC", "one", null),
                        new DeploymentRouteItem("two", 8080, "HTTP", "PUBLIC", "two", null))).block();

        assertThat(stored).hasSize(4).contains(manual, other);
        assertThat(stored).extracting(VmPortEntity::getPort).containsOnly(8080);
        assertThat(stored.subList(2, 4)).extracting(VmPortEntity::getDeploymentAppId).containsOnly("new-target");
        verify(cloudflareClient).addIngressRule("gj-test-one", "192.168.0.10", 8080, "HTTP");
        verify(cloudflareClient).addIngressRule("gj-test-two", "192.168.0.10", 8080, "HTTP");
        verify(vmPortRepository, never()).delete(any());
    }

    @Test
    void deletingOneSharedPortRouteKeepsTheOtherHostname() {
        List<VmPortEntity> stored = stubRouteStore();
        VmPortEntity first = VmPortEntity.createPublic(vmId, 8080, Protocol.HTTP, "one", "one", "dns-one");
        VmPortEntity second = VmPortEntity.createPublic(vmId, 8080, Protocol.HTTP, "two", "two", "dns-two");
        stored.addAll(List.of(first, second));
        when(vmPortRepository.findById(first.getId())).thenReturn(Mono.just(first));
        when(vmPortRepository.delete(first)).thenAnswer(invocation -> Mono.fromRunnable(() -> stored.remove(first)));
        when(accessEmailRepository.deleteAllByVmPortId(first.getId())).thenReturn(Mono.empty());
        when(cloudflareClient.removeIngressRule("one")).thenReturn(Mono.empty());
        when(cloudflareClient.deleteCname("dns-one")).thenReturn(Mono.empty());

        service.deletePort("owner-1", "owner@example.com", vmId, first.getId()).block();

        assertThat(stored).containsExactly(second);
        verify(cloudflareClient).removeIngressRule("one");
        verify(cloudflareClient).deleteCname("dns-one");
        verify(cloudflareClient, never()).removeIngressRule("two");
        verify(cloudflareClient, never()).deleteCname("dns-two");
    }

    @Test
    void duplicateGeneratedHostnameIsRejectedBeforeCloudflareChanges() {
        List<VmPortEntity> stored = stubRouteStore();
        stored.add(VmPortEntity.createPublic(UUID.randomUUID(), 80, Protocol.HTTP, "elsewhere", "gj-test-new", "existing-dns"));
        assertThatThrownBy(() -> service.addPort("owner-1", "owner@example.com", vmId,
                new PortAddRequest(8080, Protocol.HTTP, Visibility.PUBLIC, "new", List.of(), null), "token").block())
                .isInstanceOf(VmException.class).hasMessage(VmErrorCode.SUBDOMAIN_ALREADY_TAKEN.getMessage());
        verifyNoInteractions(cloudflareClient);
        assertThat(stored).hasSize(1);
    }

    @Test
    void duplicateNicknameRemainsRejected() {
        List<VmPortEntity> stored = stubRouteStore();
        stored.add(VmPortEntity.createPublic(vmId, 8080, Protocol.HTTP, "same", "existing", "existing-dns"));
        assertThatThrownBy(() -> service.addPort("owner-1", "owner@example.com", vmId,
                new PortAddRequest(8080, Protocol.HTTP, Visibility.PUBLIC, "same", List.of(), null), "token").block())
                .isInstanceOf(VmException.class).hasMessage(VmErrorCode.PORT_NICKNAME_ALREADY_EXISTS.getMessage());
        verifyNoInteractions(cloudflareClient);
    }

    @Test
    void sharedPortRoutesStillCountTowardsTheRouteLimit() {
        List<VmPortEntity> stored = stubRouteStore();
        for (int index = 0; index < 5; index++) stored.add(VmPortEntity.createPublic(vmId, 8080,
                Protocol.HTTP, "route-" + index, "route-" + index, "dns-" + index));
        assertThatThrownBy(() -> service.addPort("owner-1", "owner@example.com", vmId,
                new PortAddRequest(8080, Protocol.HTTP, Visibility.PUBLIC, "sixth", List.of(), null), "token").block())
                .isInstanceOf(VmException.class).hasMessage(VmErrorCode.PORT_LIMIT_EXCEEDED.getMessage());
        verifyNoInteractions(cloudflareClient);
    }

    @Test
    void customCnamesShareTheSamePort() {
        List<VmPortEntity> stored = stubRouteStore();
        when(userServiceClient.getUserPlan("token")).thenReturn(Mono.just("PRO"));
        for (String name : List.of("shop", "api")) {
            service.addPort("owner-1", "owner@example.com", vmId,
                    new PortAddRequest(8080, Protocol.HTTP, Visibility.PUBLIC, name, List.of(), name), "token").block();
        }
        assertThat(stored).extracting(VmPortEntity::getPort).containsExactly(8080, 8080);
        assertThat(stored).extracting(VmPortEntity::getSubdomain).containsExactly("shop", "api");
        verify(cloudflareClient).addIngressRule("shop", "192.168.0.10", 8080, "HTTP");
        verify(cloudflareClient).addIngressRule("api", "192.168.0.10", 8080, "HTTP");
    }

    @Test
    void tcpRoutesCanShareTheSamePort() {
        List<VmPortEntity> stored = stubRouteStore();
        for (String name : List.of("ssh-one", "ssh-two")) {
            service.addPort("owner-1", "owner@example.com", vmId,
                    new PortAddRequest(22, Protocol.TCP, Visibility.PUBLIC, name, List.of(), null), "token").block();
        }
        assertThat(stored).extracting(VmPortEntity::getPort).containsExactly(22, 22);
        assertThat(stored).extracting(VmPortEntity::getProtocol).containsOnly(Protocol.TCP);
    }

    private List<VmPortEntity> stubRouteStore() {
        List<VmPortEntity> stored = new ArrayList<>();
        when(vmPortRepository.countByVmId(vmId)).thenAnswer(invocation -> Mono.just(stored.stream()
                .filter(port -> port.getVmId().equals(vmId)).count()));
        when(vmPortRepository.countByVmIdAndNickname(eq(vmId), anyString())).thenAnswer(invocation -> Mono.just(stored.stream()
                .filter(port -> port.getVmId().equals(vmId) && port.getNickname().equals(invocation.getArgument(1))).count()));
        when(vmPortRepository.countBySubdomain(anyString())).thenAnswer(invocation -> Mono.just(stored.stream()
                .filter(port -> port.getSubdomain().equals(invocation.getArgument(0))).count()));
        when(vmPortRepository.save(any(VmPortEntity.class))).thenAnswer(invocation -> {
            VmPortEntity port = invocation.getArgument(0);
            stored.add(port);
            return Mono.just(port);
        });
        when(cloudflareClient.ensureCname(anyString())).thenAnswer(invocation -> Mono.just(
                new CloudflareClient.CnameRegistration("dns-" + invocation.getArgument(0), true)));
        when(cloudflareClient.addIngressRule(anyString(), anyString(), anyInt(), anyString())).thenReturn(Mono.empty());
        return stored;
    }

    private DeploymentRouteItem route(String visibility) {
        return new DeploymentRouteItem("web", 80, "HTTP", visibility, "web", "preview");
    }
}
