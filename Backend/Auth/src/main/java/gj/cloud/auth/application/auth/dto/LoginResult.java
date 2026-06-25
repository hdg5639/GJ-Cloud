package gj.cloud.auth.application.auth.dto;

public record LoginResult(String accessToken, String refreshToken, long cookieMaxAgeSeconds) {}
