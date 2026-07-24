package gj.cloud.ops.application.github.dto;

public record GithubRepositoryResponse(
        Long id,
        Long installationId,
        String fullName,
        String cloneUrl,
        String defaultBranch,
        boolean privateRepository
) {
}
