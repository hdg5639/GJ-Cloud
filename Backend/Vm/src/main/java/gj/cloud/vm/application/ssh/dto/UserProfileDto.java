package gj.cloud.vm.application.ssh.dto;

public record UserProfileDto(
    String userId,
    String email,
    String nickname,
    String profileImageUrl,
    String planType
) {}
