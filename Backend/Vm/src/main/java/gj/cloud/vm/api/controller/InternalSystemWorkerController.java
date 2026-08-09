package gj.cloud.vm.api.controller;

import gj.cloud.vm.application.systemworker.SystemWorkerProvisioningService;
import gj.cloud.vm.application.systemworker.dto.SystemWorkerProvisionRequest;
import gj.cloud.vm.application.systemworker.dto.SystemWorkerVmResponse;
import gj.cloud.vm.application.systemworker.dto.ManagedPreviewRouteRequest;
import gj.cloud.vm.application.systemworker.dto.ManagedPreviewRouteDeleteRequest;
import gj.cloud.vm.application.systemworker.dto.ManagedPreviewRouteResponse;
import gj.cloud.vm.global.response.ApiResponse;
import gj.cloud.vm.global.exception.VmException;
import gj.cloud.vm.global.exception.enums.VmErrorCode;
import gj.cloud.vm.infra.cloudflare.client.CloudflareClient;
import gj.cloud.vm.infra.cloudflare.config.CloudflareProperties;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Hidden
@RestController
@RequestMapping("/internal/automation/system-workers/auto-preview")
@RequiredArgsConstructor
public class InternalSystemWorkerController {
    private final SystemWorkerProvisioningService service;
    private final CloudflareClient cloudflareClient;
    private final CloudflareProperties cloudflareProperties;

    @PostMapping
    public Mono<ApiResponse<SystemWorkerVmResponse>> provision(@Valid @RequestBody SystemWorkerProvisionRequest body) {
        return service.provision(body).map(ApiResponse::ok);
    }
    @GetMapping("/{vmId}") public Mono<ApiResponse<SystemWorkerVmResponse>> status(@PathVariable int vmId) { return service.status(vmId).map(ApiResponse::ok); }
    @PostMapping("/{vmId}/start") public Mono<ApiResponse<SystemWorkerVmResponse>> start(@PathVariable int vmId) { return service.start(vmId).map(ApiResponse::ok); }
    @PostMapping("/{vmId}/stop") public Mono<ApiResponse<SystemWorkerVmResponse>> stop(@PathVariable int vmId) { return service.stop(vmId).map(ApiResponse::ok); }
    @PostMapping("/{vmId}/reboot") public Mono<ApiResponse<SystemWorkerVmResponse>> reboot(@PathVariable int vmId) { return service.reboot(vmId).map(ApiResponse::ok); }

    @PostMapping("/{vmId}/preview-routes")
    public Mono<ApiResponse<ManagedPreviewRouteResponse>> createRoute(
            @PathVariable int vmId, @Valid @RequestBody ManagedPreviewRouteRequest body) {
        return service.status(vmId).flatMap(vm -> {
            if (!vm.exists() || !"RUNNING".equals(vm.powerState()) || vm.internalIp() == null) {
                return Mono.error(new VmException(VmErrorCode.VM_NOT_RUNNING));
            }
            return cloudflareClient.registerCname(body.subdomain())
                    .flatMap(recordId -> cloudflareClient.addIngressRule(body.subdomain(), vm.internalIp(), body.port(), "http")
                            .thenReturn(ApiResponse.ok(new ManagedPreviewRouteResponse(
                                    body.subdomain() + "." + cloudflareProperties.getBaseDomain(), recordId)))
                            .onErrorResume(error -> cloudflareClient.deleteCname(recordId).then(Mono.error(error))));
        });
    }

    @DeleteMapping("/{vmId}/preview-routes")
    public Mono<ApiResponse<Void>> deleteRoute(
            @PathVariable int vmId, @Valid @RequestBody ManagedPreviewRouteDeleteRequest body) {
        service.validateWorkerId(vmId);
        return cloudflareClient.removeIngressRule(body.subdomain())
                .then(cloudflareClient.deleteCnameStrict(body.dnsRecordId()))
                .thenReturn(ApiResponse.ok(null));
    }
}
