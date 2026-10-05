package gj.cloud.vm.api.controller;

import gj.cloud.vm.application.port.dto.PortResponse;
import gj.cloud.vm.application.port.service.PortService;
import gj.cloud.vm.application.vm.service.VmAccessService;
import gj.cloud.vm.domain.vm.repository.VmRepository;
import gj.cloud.vm.global.security.VmPrincipal;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InternalOpsControllerTest {
    @Test
    void occupiedPortsUsePermissionCheckedVmPortSourceAndDeduplicate() {
        var service = mock(PortService.class);
        var vmId = UUID.randomUUID();
        var port = new PortResponse("port", 8080, "HTTP", "PUBLIC", "app", "example", "example.test",
                java.util.List.of(), null, null, null, null);
        when(service.getPorts("user", "user@example.test", vmId)).thenReturn(Flux.just(port, port));
        var controller = new InternalOpsController(mock(VmRepository.class), mock(VmAccessService.class), service);
        var response = controller.getOccupiedPorts(vmId, new VmPrincipal("user", "user@example.test"))
                .block(java.time.Duration.ofSeconds(1));
        assertThat(response.data()).containsExactly(8080);
        when(service.getPorts("user", "user@example.test", vmId)).thenReturn(Flux.error(new IllegalStateException("denied")));
        assertThatThrownBy(() -> controller.getOccupiedPorts(vmId, new VmPrincipal("user", "user@example.test"))
                .block(java.time.Duration.ofSeconds(1))).hasMessage("denied");
    }
}
