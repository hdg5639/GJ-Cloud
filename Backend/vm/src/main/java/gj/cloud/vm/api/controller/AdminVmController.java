package gj.cloud.vm.api.controller;

import gj.cloud.vm.application.vm.dto.VmResponse;
import gj.cloud.vm.application.vm.service.VmService;
import gj.cloud.vm.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Tag(name = "Admin - VMs")
@RestController
@RequestMapping("/admin/vms")
@RequiredArgsConstructor
public class AdminVmController {

    private final VmService vmService;

    @GetMapping
    public Mono<ApiResponse<List<VmResponse>>> listAllVms() {
        return vmService.getAllVms()
                .collectList()
                .map(ApiResponse::ok);
    }

    @GetMapping("/{vmId}")
    public Mono<ApiResponse<VmResponse>> getVm(@PathVariable UUID vmId) {
        return vmService.getVmAdmin(vmId)
                .map(ApiResponse::ok);
    }

    @DeleteMapping("/{vmId}/force")
    public Mono<ApiResponse<Void>> forceDeleteVm(@PathVariable UUID vmId) {
        return vmService.forceDeleteVm(vmId)
                .thenReturn(ApiResponse.<Void>ok(null));
    }
}
