package gj.cloud.user.application.support.dto;

import gj.cloud.user.domain.support.entity.SupportInquiryEntity;
import gj.cloud.user.domain.support.enums.SupportInquiryCategory;
import gj.cloud.user.domain.support.enums.SupportInquiryStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record SupportInquiryResponse(
        UUID id,
        String userId,
        String requesterEmail,
        SupportInquiryCategory category,
        String title,
        String content,
        String sourceArticleSlug,
        String sourceArticleTitle,
        SupportInquiryStatus status,
        String response,
        String respondedBy,
        LocalDateTime respondedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static SupportInquiryResponse from(SupportInquiryEntity entity) {
        return new SupportInquiryResponse(
                entity.getId(),
                entity.getUserId(),
                entity.getRequesterEmail(),
                entity.getCategory(),
                entity.getTitle(),
                entity.getContent(),
                entity.getSourceArticleSlug(),
                entity.getSourceArticleTitle(),
                entity.getStatus(),
                entity.getResponse(),
                entity.getRespondedBy(),
                entity.getRespondedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
