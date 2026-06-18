package gj.cloud.vm.api.controller;

import gj.cloud.vm.application.vm.dto.VmCreateRequest;
import gj.cloud.vm.application.vm.dto.VmResponse;
import gj.cloud.vm.application.vm.service.VmService;
import gj.cloud.vm.global.response.ApiResponse;
import gj.cloud.vm.global.security.VmPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/vms")
@RequiredArgsConstructor
public class VmController {

    private final VmService vmService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<ApiResponse<VmResponse>> createVm(
            @AuthenticationPrincipal VmPrincipal principal,
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody VmCreateRequest request
    ) {
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        return vmService.createVm(principal.userId(), token, request)
                .map(ApiResponse::ok);
    }

    @GetMapping
    public Mono<ApiResponse<List<VmResponse>>> getVms(@AuthenticationPrincipal VmPrincipal principal) {
        return vmService.getVms(principal.userId())
                .collectList()
                .map(ApiResponse::ok);
    }

    @GetMapping("/{vmId}")
    public Mono<ApiResponse<VmResponse>> getVm(
            @AuthenticationPrincipal VmPrincipal principal,
            @PathVariable UUID vmId
    ) {
        return vmService.getVm(principal.userId(), vmId)
                .map(ApiResponse::ok);
    }

    @DeleteMapping("/{vmId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteVm(
            @AuthenticationPrincipal VmPrincipal principal,
            @PathVariable UUID vmId
    ) {
        return vmService.deleteVm(principal.userId(), vmId);
    }
}
