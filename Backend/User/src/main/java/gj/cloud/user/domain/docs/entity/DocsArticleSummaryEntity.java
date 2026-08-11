package gj.cloud.user.domain.docs.entity;

import gj.cloud.user.domain.docs.enums.DocsArticleStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 목록 조회에서 MEDIUMTEXT 본문을 읽지 않기 위한 read-only 모델.
 * 쓰기와 상세 조회의 정본은 {@link DocsArticleEntity}다.
 */
@Entity(name = "DocsArticleSummaryEntity")
@Table(name = "docs_articles")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocsArticleSummaryEntity {

    @Id
    private UUID id;

    private String slug;
    private String title;
    private String summary;
    private String category;
    private String coverImageUrl;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "docs_article_tags", joinColumns = @JoinColumn(name = "article_id"))
    @OrderColumn(name = "tag_order")
    @Column(name = "tag")
    @BatchSize(size = 100)
    private List<String> tags = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private DocsArticleStatus status;

    private boolean featured;
    private int sortOrder;
    private long viewCount;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
