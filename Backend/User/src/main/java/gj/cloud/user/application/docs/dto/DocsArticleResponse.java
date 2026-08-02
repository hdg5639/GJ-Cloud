package gj.cloud.user.application.docs.dto;

import gj.cloud.user.domain.docs.entity.DocsArticleEntity;
import gj.cloud.user.domain.docs.enums.DocsArticleStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record DocsArticleResponse(
        UUID id,
        String slug,
        String title,
        String summary,
        String category,
        String coverImageUrl,
        String content,
        List<String> tags,
        DocsArticleStatus status,
        boolean featured,
        int sortOrder,
        long viewCount,
        String authorId,
        LocalDateTime publishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static DocsArticleResponse from(DocsArticleEntity entity) {
        return new DocsArticleResponse(
                entity.getId(),
                entity.getSlug(),
                entity.getTitle(),
                entity.getSummary(),
                entity.getCategory(),
                entity.getCoverImageUrl(),
                entity.getContent(),
                List.copyOf(entity.getTags()),
                entity.getStatus(),
                entity.isFeatured(),
                entity.getSortOrder(),
                entity.getViewCount(),
                entity.getAuthorId(),
                entity.getPublishedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
