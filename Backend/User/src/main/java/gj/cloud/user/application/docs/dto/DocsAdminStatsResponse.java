package gj.cloud.user.application.docs.dto;

public record DocsAdminStatsResponse(
        long total,
        long published,
        long drafts,
        long categories
) {
}
