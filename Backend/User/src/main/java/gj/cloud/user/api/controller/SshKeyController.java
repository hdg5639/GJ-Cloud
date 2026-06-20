package gj.cloud.user.api.controller;

import gj.cloud.user.api.controller.spec.SshKeyApi;
import gj.cloud.user.application.sshkey.dto.SshKeyGenerateRequest;
import gj.cloud.user.application.sshkey.dto.SshKeyGenerateResponse;
import gj.cloud.user.application.sshkey.dto.SshKeyRegisterRequest;
import gj.cloud.user.application.sshkey.dto.SshKeyResponse;
import gj.cloud.user.application.sshkey.service.SshKeyService;
import gj.cloud.user.global.response.ApiResponse;
import gj.cloud.user.global.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class SshKeyController implements SshKeyApi {

    private final SshKeyService sshKeyService;

    @Override
    public ApiResponse<List<SshKeyResponse>> getKeys(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(sshKeyService.getKeys(principal.userId()));
    }

    @Override
    public ApiResponse<SshKeyResponse> registerKey(
            @AuthenticationPrincipal UserPrincipal principal,
            SshKeyRegisterRequest request
    ) {
        return ApiResponse.ok(sshKeyService.registerKey(principal.userId(), request));
    }

    @Override
    public ApiResponse<SshKeyGenerateResponse> generateKey(
            @AuthenticationPrincipal UserPrincipal principal,
            SshKeyGenerateRequest request
    ) {
        return ApiResponse.ok(sshKeyService.generateKey(principal.userId(), request));
    }

    @Override
    public ApiResponse<Void> deleteKey(
            @AuthenticationPrincipal UserPrincipal principal,
            String keyId
    ) {
        sshKeyService.deleteKey(principal.userId(), keyId);
        return ApiResponse.ok();
    }
}
