package gj.cloud.vm.global.auth.dto;

public record ServiceTokenResponse(String accessToken, String tokenType, long expiresIn) {
}
