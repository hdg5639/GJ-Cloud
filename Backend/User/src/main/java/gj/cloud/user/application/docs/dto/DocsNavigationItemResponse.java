package gj.cloud.user.application.docs.dto;

import gj.cloud.user.domain.docs.entity.DocsArticleSummaryEntity;

import java.util.UUID;

public record DocsNavigationItemResponse(UUID id, String slug, String title) {
    public static DocsNavigationItemResponse from(DocsArticleSummaryEntity entity) {
        return new DocsNavigationItemResponse(entity.getId(), entity.getSlug(), entity.getTitle());
    }
}
