package gj.cloud.ops.application.github.dto;

import java.util.List;

public record GithubInstallationCompleteResponse(
        List<GithubInstallationResponse> installations,
        String vmId
) {
}
