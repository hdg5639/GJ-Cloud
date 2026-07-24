package gj.cloud.ops.api.controller;

import gj.cloud.ops.application.managementkey.dto.ManagementKeyResponse;
import gj.cloud.ops.application.managementkey.service.ManagementKeyService;
import gj.cloud.ops.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

// VM 서비스(aud=vm-service) 전용 내부 API. VM 생성 시 관리 키 발급, 삭제 시 폐기 요청을 받음.
@Hidden
@RestController
@RequestMapping("/internal/vms/{vmId}/management-key")
@RequiredArgsConstructor
public class InternalVmKeyController {

    private final ManagementKeyService managementKeyService;

    @PutMapping
    public ApiResponse<ManagementKeyResponse> issue(@PathVariable String vmId) {
        String publicKey = managementKeyService.issue(vmId);
        return ApiResponse.ok(new ManagementKeyResponse(publicKey));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable String vmId) {
        managementKeyService.revoke(vmId);
    }
}
