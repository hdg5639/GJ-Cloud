package gj.cloud.vm.api.controller;

import gj.cloud.vm.application.port.dto.DeploymentRoutesSyncRequest;
import gj.cloud.vm.application.port.service.PortService;
import gj.cloud.vm.application.vm.dto.VmContextResponse;
import gj.cloud.vm.application.vm.service.VmAccessService;
import gj.cloud.vm.domain.vm.repository.VmRepository;
import gj.cloud.vm.global.exception.VmException;
import gj.cloud.vm.global.exception.enums.VmErrorCode;
import gj.cloud.vm.global.response.ApiResponse;
import gj.cloud.vm.global.security.VmPrincipal;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

// Ops 서비스(aud=ops-service) 전용 내부 API. VM 권한 판정은 VM 서비스가 단일 진실 공급원이므로
// Ops는 터미널/파일/배포 요청마다 이 API로 권한을 질의하고 VM 서비스 DB를 직접 조회하지 않음.
@Hidden
@RestController
@RequestMapping("/internal/ops")
@RequiredArgsConstructor
public class InternalOpsController {

    private final VmRepository vmRepository;
    private final VmAccessService vmAccessService;
    private final PortService portService;

    @GetMapping("/vms/{vmId}/context")
    public Mono<ApiResponse<VmContextResponse>> getContext(
            @PathVariable UUID vmId,
            @AuthenticationPrincipal VmPrincipal principal
    ) {
        return vmRepository.findById(vmId)
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND)))
                .flatMap(vm -> {
                    if (vm.getDeletedAt() != null) {
                        return Mono.error(new VmException(VmErrorCode.VM_NOT_FOUND));
                    }
                    return vmAccessService.resolveContext(vmId, vm.getUserId(), principal.userId(), principal.email())
                            .map(access -> VmContextResponse.from(vm, access));
                })
                .map(ApiResponse::ok);
    }

    // 1.5절 규칙1 — 배포마다 포트를 누적 추가하는 게 아니라, 현재 배포가 원하는 route 집합으로 동기화(PUT)
    @PutMapping("/vms/{vmId}/deployment-routes")
    public Mono<ApiResponse<Void>> syncDeploymentRoutes(
            @PathVariable UUID vmId,
            @AuthenticationPrincipal VmPrincipal principal,
            @Valid @RequestBody DeploymentRoutesSyncRequest request,
            ServerWebExchange exchange
    ) {
        // PRO 커스텀 CNAME 검증(User 서비스 plan 조회)에 필요 — 이 토큰은 Ops가 그대로 포워딩한 것이라
        // aud=ops-service이고, User 쪽 InternalPlanJwtValidator가 이 audience를 허용하도록 완화돼 있음.
        String bearerToken = extractToken(exchange);
        return portService.syncDeploymentRoutes(principal.userId(), principal.email(), vmId,
                        request.deploymentId(), request.routes(), bearerToken)
                .thenReturn(ApiResponse.<Void>ok(null));
    }

    private String extractToken(ServerWebExchange exchange) {
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        return (header != null && header.startsWith("Bearer ")) ? header.substring(7) : "";
    }
}
