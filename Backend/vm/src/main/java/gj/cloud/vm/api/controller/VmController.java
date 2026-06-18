package gj.cloud.vm.api.controller;

import gj.cloud.vm.application.vm.dto.VmCreateRequest;
import gj.cloud.vm.application.vm.dto.VmPlanUpdateRequest;
import gj.cloud.vm.application.vm.dto.VmPowerRequest;
import gj.cloud.vm.application.vm.dto.VmResponse;
import gj.cloud.vm.application.vm.service.VmService;
import gj.cloud.vm.global.response.ApiResponse;
import gj.cloud.vm.global.security.VmPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Tag(name = "VM", description = "VM 생성 · 조회 · 전원 · 삭제")
@RestController
@RequestMapping("/vms")
@RequiredArgsConstructor
public class VmController {

    private final VmService vmService;

    @Operation(summary = "VM 생성", description = "VM을 비동기로 생성합니다. 즉시 202를 반환하고 상태는 SSE로 수신하세요.")
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<ApiResponse<VmResponse>> createVm(
            @AuthenticationPrincipal VmPrincipal principal,
            ServerWebExchange exchange,
            @Valid @RequestBody VmCreateRequest request
    ) {
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        String token = authHeader != null && authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        return vmService.createVmWithEmail(principal.userId(), token, request, principal.email())
                .map(ApiResponse::ok);
    }

    @Operation(summary = "VM 목록 조회")
    @GetMapping
    public Mono<ApiResponse<List<VmResponse>>> getVms(@AuthenticationPrincipal VmPrincipal principal) {
        return vmService.getVms(principal.userId())
                .collectList()
                .map(ApiResponse::ok);
    }

    @Operation(summary = "VM 단건 조회")
    @GetMapping("/{vmId}")
    public Mono<ApiResponse<VmResponse>> getVm(
            @AuthenticationPrincipal VmPrincipal principal,
            @PathVariable UUID vmId
    ) {
        return vmService.getVm(principal.userId(), vmId)
                .map(ApiResponse::ok);
    }

    @Operation(summary = "VM 삭제", description = "실행 중인 VM은 자동으로 정지 후 삭제됩니다.")
    @DeleteMapping("/{vmId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteVm(
            @AuthenticationPrincipal VmPrincipal principal,
            @PathVariable UUID vmId
    ) {
        return vmService.deleteVm(principal.userId(), vmId);
    }

    @Operation(summary = "VM 전원 제어", description = "START(시작) · STOP(정지) · SUSPEND(일시정지) · REBOOT(재시작)")
    @PatchMapping("/{vmId}/power")
    public Mono<ApiResponse<VmResponse>> changePower(
            @AuthenticationPrincipal VmPrincipal principal,
            @PathVariable UUID vmId,
            @Valid @RequestBody VmPowerRequest request
    ) {
        return vmService.changePower(principal.userId(), vmId, request)
                .map(ApiResponse::ok);
    }

    @Operation(summary = "VM 플랜 변경", description = "플랜(FREE↔PRO) 및 디스크 크기 변경. 디스크 축소 불가.")
    @PatchMapping("/{vmId}/plan")
    public Mono<ApiResponse<VmResponse>> updatePlan(
            @AuthenticationPrincipal VmPrincipal principal,
            @PathVariable UUID vmId,
            @Valid @RequestBody VmPlanUpdateRequest request
    ) {
        return vmService.updatePlan(principal.userId(), vmId, request)
                .map(ApiResponse::ok);
    }
}
