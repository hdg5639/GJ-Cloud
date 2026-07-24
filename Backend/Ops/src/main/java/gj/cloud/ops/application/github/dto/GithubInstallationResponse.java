package gj.cloud.ops.application.github.dto;

import gj.cloud.ops.domain.github.entity.GithubInstallationEntity;

public record GithubInstallationResponse(
        Long installationId,
        String accountLogin,
        String accountType
) {
    public static GithubInstallationResponse from(GithubInstallationEntity entity) {
        return new GithubInstallationResponse(
                entity.getInstallationId(), entity.getAccountLogin(), entity.getAccountType());
    }
}
