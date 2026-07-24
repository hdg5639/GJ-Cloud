package gj.cloud.auth.application.auth.dto;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {}
