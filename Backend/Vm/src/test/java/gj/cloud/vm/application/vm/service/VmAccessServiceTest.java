package gj.cloud.vm.application.vm.service;

import gj.cloud.vm.application.vm.dto.VmAccessContext;
import gj.cloud.vm.domain.org.entity.OrganizationMemberEntity;
import gj.cloud.vm.domain.org.entity.OrganizationVmEntity;
import gj.cloud.vm.domain.org.enums.MemberRole;
import gj.cloud.vm.domain.org.repository.OrganizationMemberRepository;
import gj.cloud.vm.domain.org.repository.OrganizationVmRepository;
import gj.cloud.vm.domain.vm.enums.VmPermission;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VmAccessServiceTest {

    private final OrganizationVmRepository organizationVmRepository = mock(OrganizationVmRepository.class);
    private final OrganizationMemberRepository organizationMemberRepository = mock(OrganizationMemberRepository.class);
    private final VmAccessService service = new VmAccessService(
            organizationVmRepository, organizationMemberRepository);

    @Test
    void ownerReceivesBackupReadPermission() {
        VmAccessContext context = service.resolveContext(
                UUID.randomUUID(), "owner-id", "owner-id", "owner@example.com").block();

        assertThat(context).isNotNull();
        assertThat(context.role()).isEqualTo(MemberRole.OWNER);
        assertThat(context.permissions()).contains(VmPermission.BACKUP_READ);
    }

    @Test
    void memberDoesNotReceiveBackupReadPermission() {
        UUID vmId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        String email = "member@example.com";
        OrganizationVmEntity organizationVm = OrganizationVmEntity.create(organizationId, vmId);
        OrganizationMemberEntity member = OrganizationMemberEntity
                .createInvite(organizationId, email, MemberRole.MEMBER)
                .withAccepted("member-id");
        when(organizationVmRepository.findAllByVmId(vmId)).thenReturn(Flux.just(organizationVm));
        when(organizationMemberRepository.findAcceptedByOrgIdAndEmail(organizationId, email))
                .thenReturn(Mono.just(member));

        VmAccessContext context = service.resolveContext(vmId, "owner-id", "member-id", email).block();

        assertThat(context).isNotNull();
        assertThat(context.role()).isEqualTo(MemberRole.MEMBER);
        assertThat(context.permissions())
                .contains(VmPermission.FILE_READ)
                .doesNotContain(VmPermission.BACKUP_READ, VmPermission.DEPLOY);
    }
}
