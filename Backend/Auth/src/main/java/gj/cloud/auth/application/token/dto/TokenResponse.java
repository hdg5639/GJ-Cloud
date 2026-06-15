package gj.cloud.auth.application.token.dto;

public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {}
