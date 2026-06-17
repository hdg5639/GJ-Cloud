package gj.cloud.user.api.controller.spec;

import gj.cloud.user.application.sshkey.dto.SshKeyRegisterRequest;
import gj.cloud.user.application.sshkey.dto.SshKeyResponse;
import gj.cloud.user.global.response.ApiResponse;
import gj.cloud.user.global.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "SSH Key", description = "SSH 공개키 관리 API")
@RequestMapping("/users/ssh-keys")
public interface SshKeyApi {

    @Operation(summary = "SSH 키 목록 조회")
    @GetMapping
    ApiResponse<List<SshKeyResponse>> getKeys(@AuthenticationPrincipal UserPrincipal principal);

    @Operation(summary = "SSH 키 등록")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<SshKeyResponse> registerKey(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody SshKeyRegisterRequest request
    );

    @Operation(summary = "SSH 키 삭제")
    @DeleteMapping("/{keyId}")
    ApiResponse<Void> deleteKey(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String keyId
    );
}
