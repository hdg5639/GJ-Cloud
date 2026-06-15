package gj.cloud.auth.api.controller.spec;

import gj.cloud.auth.application.auth.dto.LoginRequest;
import gj.cloud.auth.application.auth.dto.LoginResponse;
import gj.cloud.auth.application.auth.dto.RegisterRequest;
import gj.cloud.auth.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/auth")
public interface AuthApi {

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<Void> register(@Valid @RequestBody RegisterRequest request);

    @PostMapping("/login")
    ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request);

    @PostMapping("/logout")
    ApiResponse<Void> logout(@AuthenticationPrincipal String userId);

    @DeleteMapping("/withdraw")
    ApiResponse<Void> withdraw(@AuthenticationPrincipal String userId);
}
