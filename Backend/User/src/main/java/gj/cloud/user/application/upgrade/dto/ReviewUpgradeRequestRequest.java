package gj.cloud.user.application.upgrade.dto;

public record ReviewUpgradeRequestRequest(
        boolean approved,
        String reason
) {}
