package gj.cloud.user.domain.docs.entity;

import gj.cloud.user.domain.docs.enums.DocsArticleStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "docs_articles")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class DocsArticleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 120)
    private String slug;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(nullable = false, length = 400)
    private String summary;

    @Column(nullable = false, length = 80)
    private String category;

    @Column(length = 500)
    private String coverImageUrl;

    @Lob
    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "docs_article_tags", joinColumns = @JoinColumn(name = "article_id"))
    @OrderColumn(name = "tag_order")
    @Column(name = "tag", nullable = false, length = 60)
    @Builder.Default
    private List<String> tags = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocsArticleStatus status;

    @Column(nullable = false)
    private boolean featured;

    @Column(nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private long viewCount;

    @Column(nullable = false, length = 36)
    private String authorId;

    private LocalDateTime publishedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static DocsArticleEntity create(
            String authorId,
            String slug,
            String title,
            String summary,
            String category,
            String coverImageUrl,
            String content,
            List<String> tags,
            boolean featured,
            int sortOrder
    ) {
        LocalDateTime now = LocalDateTime.now();
        return DocsArticleEntity.builder()
                .authorId(authorId)
                .slug(slug)
                .title(title)
                .summary(summary)
                .category(category)
                .coverImageUrl(coverImageUrl)
                .content(content)
                .tags(new ArrayList<>(tags))
                .status(DocsArticleStatus.DRAFT)
                .featured(featured)
                .sortOrder(sortOrder)
                .viewCount(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public void update(
            String slug,
            String title,
            String summary,
            String category,
            String coverImageUrl,
            String content,
            List<String> tags,
            boolean featured,
            int sortOrder
    ) {
        this.slug = slug;
        this.title = title;
        this.summary = summary;
        this.category = category;
        this.coverImageUrl = coverImageUrl;
        this.content = content;
        this.tags.clear();
        this.tags.addAll(tags);
        this.featured = featured;
        this.sortOrder = sortOrder;
        this.updatedAt = LocalDateTime.now();
    }

    public void publish() {
        this.status = DocsArticleStatus.PUBLISHED;
        if (this.publishedAt == null) {
            this.publishedAt = LocalDateTime.now();
        }
        this.updatedAt = LocalDateTime.now();
    }

    public void unpublish() {
        this.status = DocsArticleStatus.DRAFT;
        this.updatedAt = LocalDateTime.now();
    }

    public void recordView() {
        this.viewCount += 1;
    }
}
