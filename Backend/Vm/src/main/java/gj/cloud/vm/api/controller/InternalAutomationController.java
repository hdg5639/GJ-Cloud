package gj.cloud.vm.api.controller;

import gj.cloud.vm.application.port.dto.AutomationDeploymentRoutesRequest;
import gj.cloud.vm.application.port.service.PortService;
import gj.cloud.vm.application.vm.dto.VmContextResponse;
import gj.cloud.vm.application.vm.dto.VmExistenceRequest;
import gj.cloud.vm.application.vm.dto.VmExistenceResponse;
import gj.cloud.vm.application.vm.service.VmAccessService;
import gj.cloud.vm.domain.vm.enums.VmPermission;
import gj.cloud.vm.domain.vm.repository.VmRepository;
import gj.cloud.vm.global.exception.VmException;
import gj.cloud.vm.global.exception.enums.VmErrorCode;
import gj.cloud.vm.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.LinkedHashSet;
import java.util.UUID;

@Hidden
@RestController
@RequestMapping("/internal/automation")
@RequiredArgsConstructor
public class InternalAutomationController {

    private final VmRepository vmRepository;
    private final VmAccessService vmAccessService;
    private final PortService portService;

    @GetMapping("/vms/{vmId}/context")
    public Mono<ApiResponse<VmContextResponse>> getContext(
            @PathVariable UUID vmId,
            @RequestParam String ownerUserId,
            @RequestParam String ownerEmail
    ) {
        return vmRepository.findById(vmId)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND)))
                .filter(vm -> vm.getDeletedAt() == null)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND)))
                .flatMap(vm -> vmAccessService.resolveContext(vmId, vm.getUserId(), ownerUserId, ownerEmail)
                        .filter(access -> access.permissions().contains(VmPermission.DEPLOY))
                        .switchIfEmpty(Mono.error(new VmException(VmErrorCode.FORBIDDEN)))
                        .map(access -> VmContextResponse.from(vm, access)))
                .map(ApiResponse::ok);
    }

    @PostMapping("/vms/existence")
    public Mono<ApiResponse<VmExistenceResponse>> findExistingVms(
            @Valid @RequestBody VmExistenceRequest request
    ) {
        return vmRepository.findExistingIds(request.vmIds())
                .collectList()
                .map(ids -> ApiResponse.ok(new VmExistenceResponse(new LinkedHashSet<>(ids))));
    }

    @PutMapping("/vms/{vmId}/deployment-routes")
    public Mono<ApiResponse<Void>> syncDeploymentRoutes(
            @PathVariable UUID vmId,
            @Valid @RequestBody AutomationDeploymentRoutesRequest request
    ) {
        return portService.syncDeploymentRoutesAutomation(
                        request.ownerUserId(),
                        request.ownerEmail(),
                        vmId,
                        request.routes().deploymentAppId(),
                        request.routes().deploymentId(),
                        request.routes().routes())
                .thenReturn(ApiResponse.<Void>ok(null));
    }
}
