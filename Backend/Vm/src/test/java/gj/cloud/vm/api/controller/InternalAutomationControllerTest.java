package gj.cloud.vm.api.controller;

import gj.cloud.vm.application.port.service.PortService;
import gj.cloud.vm.application.vm.dto.VmExistenceRequest;
import gj.cloud.vm.application.vm.service.VmAccessService;
import gj.cloud.vm.domain.vm.repository.VmRepository;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InternalAutomationControllerTest {

    @Test
    void returnsOnlyExistingNonDeletedVmIds() {
        VmRepository repository = mock(VmRepository.class);
        UUID existing = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID missing = UUID.fromString("00000000-0000-0000-0000-000000000002");
        when(repository.findExistingIds(List.of(existing, missing))).thenReturn(Flux.just(existing));
        InternalAutomationController controller = new InternalAutomationController(
                repository, mock(VmAccessService.class), mock(PortService.class));

        var response = controller.findExistingVms(new VmExistenceRequest(List.of(existing, missing))).block();

        assertThat(response).isNotNull();
        assertThat(response.data().existingVmIds()).containsExactly(existing);
    }
}
