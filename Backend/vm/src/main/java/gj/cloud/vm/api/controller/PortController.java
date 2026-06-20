package gj.cloud.vm.api.controller;

import gj.cloud.vm.application.port.dto.PortAccessAddRequest;
import gj.cloud.vm.application.port.dto.PortAddRequest;
import gj.cloud.vm.application.port.dto.PortResponse;
import gj.cloud.vm.application.port.service.PortService;
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
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Tag(name = "Port", description = "VM 포트 노출 관리 API")
@RestController
@RequestMapping("/vms/{vmId}/ports")
@RequiredArgsConstructor
public class PortController {

    private final PortService portService;

    @Operation(summary = "포트 추가", description = "VM에 포트를 Cloudflare Tunnel로 노출합니다. PUBLIC은 Access 없이, PRIVATE은 이메일 인증 필요.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ApiResponse<PortResponse>> addPort(
            @AuthenticationPrincipal VmPrincipal principal,
            @PathVariable UUID vmId,
            @Valid @RequestBody PortAddRequest request
    ) {
        return portService.addPort(principal.userId(), principal.email(), vmId, request)
                .map(ApiResponse::ok);
    }

    @Operation(summary = "포트 목록 조회")
    @GetMapping
    public Mono<ApiResponse<List<PortResponse>>> getPorts(
            @AuthenticationPrincipal VmPrincipal principal,
            @PathVariable UUID vmId
    ) {
        return portService.getPorts(principal.userId(), vmId)
                .collectList()
                .map(ApiResponse::ok);
    }

    @Operation(summary = "포트 삭제", description = "Cloudflare CNAME, ingress, Access 리소스를 모두 정리합니다.")
    @DeleteMapping("/{portId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deletePort(
            @AuthenticationPrincipal VmPrincipal principal,
            @PathVariable UUID vmId,
            @PathVariable UUID portId
    ) {
        return portService.deletePort(principal.userId(), vmId, portId);
    }

    @Operation(summary = "비공개 포트 접근 이메일 추가")
    @PostMapping("/{portId}/access")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ApiResponse<PortResponse>> addPortAccessEmail(
            @AuthenticationPrincipal VmPrincipal principal,
            @PathVariable UUID vmId,
            @PathVariable UUID portId,
            @Valid @RequestBody PortAccessAddRequest request
    ) {
        return portService.addPortAccessEmail(principal.userId(), vmId, portId, request)
                .map(ApiResponse::ok);
    }

    @Operation(summary = "비공개 포트 접근 이메일 제거")
    @DeleteMapping("/{portId}/access/{email}")
    public Mono<ApiResponse<PortResponse>> removePortAccessEmail(
            @AuthenticationPrincipal VmPrincipal principal,
            @PathVariable UUID vmId,
            @PathVariable UUID portId,
            @PathVariable String email
    ) {
        return portService.removePortAccessEmail(principal.userId(), vmId, portId, email)
                .map(ApiResponse::ok);
    }
}
