package gj.cloud.ops.api.controller;

import gj.cloud.ops.application.sshreadiness.dto.SshReadinessRequest;
import gj.cloud.ops.application.sshreadiness.dto.SshReadinessResponse;
import gj.cloud.ops.application.sshreadiness.service.VmSshReadinessService;
import gj.cloud.ops.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@RequestMapping("/internal/vms/{vmId}/ssh-readiness")
@RequiredArgsConstructor
public class InternalVmSshController {

    private final VmSshReadinessService sshReadinessService;

    @PostMapping
    public ApiResponse<SshReadinessResponse> ensureReady(
            @PathVariable String vmId,
            @Valid @RequestBody SshReadinessRequest request
    ) {
        return ApiResponse.ok(sshReadinessService.ensureReady(vmId, request));
    }
}
