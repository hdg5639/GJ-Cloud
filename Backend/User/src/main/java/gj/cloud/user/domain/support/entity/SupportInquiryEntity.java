package gj.cloud.user.domain.support.entity;

import gj.cloud.user.domain.support.enums.SupportInquiryCategory;
import gj.cloud.user.domain.support.enums.SupportInquiryStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "support_inquiries")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class SupportInquiryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 36)
    private String userId;

    @Column(nullable = false, length = 255)
    private String requesterEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SupportInquiryCategory category;

    @Column(nullable = false, length = 120)
    private String title;

    @Lob
    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;

    @Column(length = 120)
    private String sourceArticleSlug;

    @Column(length = 180)
    private String sourceArticleTitle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SupportInquiryStatus status;

    @Lob
    @Column(columnDefinition = "MEDIUMTEXT")
    private String response;

    @Column(length = 36)
    private String respondedBy;

    private LocalDateTime respondedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static SupportInquiryEntity create(
            String userId,
            String requesterEmail,
            SupportInquiryCategory category,
            String title,
            String content,
            String sourceArticleSlug,
            String sourceArticleTitle
    ) {
        LocalDateTime now = LocalDateTime.now();
        return SupportInquiryEntity.builder()
                .userId(userId)
                .requesterEmail(requesterEmail)
                .category(category)
                .title(title)
                .content(content)
                .sourceArticleSlug(sourceArticleSlug)
                .sourceArticleTitle(sourceArticleTitle)
                .status(SupportInquiryStatus.OPEN)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public void answer(String adminId, String response) {
        LocalDateTime now = LocalDateTime.now();
        this.response = response;
        this.respondedBy = adminId;
        this.respondedAt = now;
        this.status = SupportInquiryStatus.ANSWERED;
        this.updatedAt = now;
    }

    public void close() {
        this.status = SupportInquiryStatus.CLOSED;
        this.updatedAt = LocalDateTime.now();
    }

    public void reopen() {
        this.status = SupportInquiryStatus.OPEN;
        this.updatedAt = LocalDateTime.now();
    }
}
