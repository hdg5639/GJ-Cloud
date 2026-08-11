package gj.cloud.user.application.docs.dto;

import gj.cloud.user.domain.docs.entity.DocsArticleEntity;
import gj.cloud.user.domain.docs.entity.DocsArticleSummaryEntity;
import gj.cloud.user.domain.docs.enums.DocsArticleStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record DocsArticleSummaryResponse(
        UUID id,
        String slug,
        String title,
        String summary,
        String category,
        String coverImageUrl,
        List<String> tags,
        DocsArticleStatus status,
        boolean featured,
        int sortOrder,
        long viewCount,
        LocalDateTime publishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static DocsArticleSummaryResponse from(DocsArticleEntity entity) {
        return new DocsArticleSummaryResponse(
                entity.getId(),
                entity.getSlug(),
                entity.getTitle(),
                entity.getSummary(),
                entity.getCategory(),
                entity.getCoverImageUrl(),
                List.copyOf(entity.getTags()),
                entity.getStatus(),
                entity.isFeatured(),
                entity.getSortOrder(),
                entity.getViewCount(),
                entity.getPublishedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public static DocsArticleSummaryResponse from(DocsArticleSummaryEntity entity) {
        return new DocsArticleSummaryResponse(
                entity.getId(),
                entity.getSlug(),
                entity.getTitle(),
                entity.getSummary(),
                entity.getCategory(),
                entity.getCoverImageUrl(),
                List.copyOf(entity.getTags()),
                entity.getStatus(),
                entity.isFeatured(),
                entity.getSortOrder(),
                entity.getViewCount(),
                entity.getPublishedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
