package gj.cloud.auth.application.token.dto;

public record RefreshResult(String accessToken, String newRefreshToken, long cookieMaxAgeSeconds) {}
