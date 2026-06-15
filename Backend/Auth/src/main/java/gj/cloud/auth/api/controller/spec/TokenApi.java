package gj.cloud.auth.api.controller.spec;

import gj.cloud.auth.application.auth.dto.LoginResponse;
import gj.cloud.auth.application.auth.dto.TokenExchangeRequest;
import gj.cloud.auth.application.auth.dto.TokenRefreshRequest;
import gj.cloud.auth.application.token.dto.TokenResponse;
import gj.cloud.auth.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/auth")
public interface TokenApi {

    @PostMapping("/token/refresh")
    ApiResponse<LoginResponse> refresh(@Valid @RequestBody TokenRefreshRequest request);

    @PostMapping("/token/exchange")
    ApiResponse<TokenResponse> exchange(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody TokenExchangeRequest request);

    @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> jwks();
}
