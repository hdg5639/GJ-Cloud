package gj.cloud.ops.application.github.dto;

public record GithubRepositoryAccess(
        Long installationId,
        Long repositoryId,
        String fullName,
        String cloneUrl,
        String defaultBranch,
        String token
) {
}
