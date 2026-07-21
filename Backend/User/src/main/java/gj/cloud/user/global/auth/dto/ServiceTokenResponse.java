package gj.cloud.user.global.auth.dto;

public record ServiceTokenResponse(String accessToken, String tokenType, long expiresIn) {
}
