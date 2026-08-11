package gj.cloud.user.application.docs.service;

import gj.cloud.user.application.docs.dto.*;
import gj.cloud.user.domain.docs.entity.DocsArticleEntity;
import gj.cloud.user.domain.docs.entity.DocsArticleSummaryEntity;
import gj.cloud.user.domain.docs.enums.DocsArticleStatus;
import gj.cloud.user.domain.docs.repository.DocsArticleRepository;
import gj.cloud.user.domain.docs.repository.DocsArticleSummaryRepository;
import gj.cloud.user.global.exception.UserException;
import gj.cloud.user.global.exception.enums.UserErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DocsArticleService {

    private final DocsArticleRepository repository;
    private final DocsArticleSummaryRepository summaryRepository;

    @Transactional(readOnly = true)
    public List<DocsArticleSummaryResponse> listPublished(String query, String category) {
        return listPublishedPage(query, category, 1, 48).getContent();
    }

    @Transactional(readOnly = true)
    public Page<DocsArticleSummaryResponse> listPublishedPage(
            String query, String category, int page, int size
    ) {
        String normalizedQuery = normalizeSearch(query);
        String normalizedCategory = normalizeSearch(category);
        PageRequest pageable = pageRequest(page, size, 48);
        Page<DocsArticleSummaryEntity> articles;
        if (normalizedQuery.isBlank()) {
            articles = normalizedCategory.isBlank()
                    ? summaryRepository.findAllByStatusOrderByFeaturedDescSortOrderAscPublishedAtDesc(
                            DocsArticleStatus.PUBLISHED, pageable)
                    : summaryRepository.findAllByStatusAndCategoryIgnoreCaseOrderByFeaturedDescSortOrderAscPublishedAtDesc(
                            DocsArticleStatus.PUBLISHED, normalizedCategory, pageable);
        } else if (normalizedQuery.length() < 2) {
            articles = summaryRepository.searchPublishedContains(
                    DocsArticleStatus.PUBLISHED, normalizedCategory, normalizedQuery, pageable);
        } else {
            articles = summaryRepository.searchPublished(
                    DocsArticleStatus.PUBLISHED.name(), normalizedCategory, normalizedQuery, pageable);
            if (articles.getTotalElements() == 0) {
                articles = summaryRepository.searchPublishedContains(
                        DocsArticleStatus.PUBLISHED, normalizedCategory, normalizedQuery, pageable);
            }
        }
        return articles.map(DocsArticleSummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public List<DocsArticleSummaryResponse> listFeatured() {
        return summaryRepository
                .findTop2ByStatusAndFeaturedTrueOrderBySortOrderAscPublishedAtDesc(DocsArticleStatus.PUBLISHED)
                .stream()
                .map(DocsArticleSummaryResponse::from)
                .toList();
    }

    @Transactional
    public DocsArticleResponse getPublished(String slug) {
        DocsArticleEntity article = repository.findBySlugAndStatus(normalizeSlug(slug), DocsArticleStatus.PUBLISHED)
                .orElseThrow(() -> new UserException(UserErrorCode.DOCS_ARTICLE_NOT_FOUND));
        article.recordView();
        return DocsArticleResponse.from(article);
    }

    @Transactional
    public DocsArticlePageResponse getPublishedPage(String slug) {
        DocsArticleEntity article = repository.findBySlugAndStatus(normalizeSlug(slug), DocsArticleStatus.PUBLISHED)
                .orElseThrow(() -> new UserException(UserErrorCode.DOCS_ARTICLE_NOT_FOUND));
        article.recordView();
        List<DocsNavigationItemResponse> sameCategory = summaryRepository
                .findTop100ByStatusAndCategoryIgnoreCaseOrderByFeaturedDescSortOrderAscPublishedAtDesc(
                        DocsArticleStatus.PUBLISHED, article.getCategory())
                .stream()
                .map(DocsNavigationItemResponse::from)
                .toList();
        return new DocsArticlePageResponse(DocsArticleResponse.from(article), sameCategory);
    }

    @Transactional(readOnly = true)
    public List<DocsCategoryResponse> listCategories() {
        return summaryRepository.countByCategory(DocsArticleStatus.PUBLISHED).stream()
                .map(row -> new DocsCategoryResponse(row.getName(), row.getArticleCount()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DocsArticleSummaryResponse> listAdmin() {
        return listAdminPage(null, null, 1, 100).getContent();
    }

    @Transactional(readOnly = true)
    public Page<DocsArticleSummaryResponse> listAdminPage(
            String query, DocsArticleStatus status, int page, int size
    ) {
        String normalizedQuery = normalizeSearch(query);
        PageRequest pageable = pageRequest(page, size, 100);
        Page<DocsArticleSummaryEntity> articles;
        if (normalizedQuery.isBlank()) {
            articles = status == null
                    ? summaryRepository.findAllByOrderByUpdatedAtDesc(pageable)
                    : summaryRepository.findAllByStatusOrderByUpdatedAtDesc(status, pageable);
        } else if (normalizedQuery.length() < 2) {
            articles = summaryRepository.searchAdminContains(status, normalizedQuery, pageable);
        } else {
            articles = summaryRepository.searchAdmin(
                    status == null ? "" : status.name(), normalizedQuery, pageable);
            if (articles.getTotalElements() == 0) {
                articles = summaryRepository.searchAdminContains(status, normalizedQuery, pageable);
            }
        }
        return articles.map(DocsArticleSummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public DocsAdminStatsResponse getAdminStats() {
        long published = summaryRepository.countByStatus(DocsArticleStatus.PUBLISHED);
        long drafts = summaryRepository.countByStatus(DocsArticleStatus.DRAFT);
        return new DocsAdminStatsResponse(
                published + drafts,
                published,
                drafts,
                summaryRepository.countDistinctCategories());
    }

    @Transactional(readOnly = true)
    public DocsArticleResponse getAdmin(UUID id) {
        return DocsArticleResponse.from(find(id));
    }

    @Transactional
    public DocsArticleResponse create(String adminId, DocsArticleUpsertRequest request) {
        String slug = resolveSlug(request.slug(), request.title());
        if (repository.existsBySlug(slug)) {
            throw new UserException(UserErrorCode.DOCS_SLUG_ALREADY_EXISTS);
        }
        DocsArticleEntity article = DocsArticleEntity.create(
                adminId,
                slug,
                cleanRequired(request.title()),
                cleanRequired(request.summary()),
                cleanRequired(request.category()),
                cleanOptional(request.coverImageUrl()),
                request.content().trim(),
                cleanTags(request.tags()),
                request.featured(),
                request.sortOrder()
        );
        return DocsArticleResponse.from(repository.save(article));
    }

    @Transactional
    public DocsArticleResponse update(UUID id, DocsArticleUpsertRequest request) {
        DocsArticleEntity article = find(id);
        String slug = resolveSlug(request.slug(), request.title());
        if (repository.existsBySlugAndIdNot(slug, id)) {
            throw new UserException(UserErrorCode.DOCS_SLUG_ALREADY_EXISTS);
        }
        article.update(
                slug,
                cleanRequired(request.title()),
                cleanRequired(request.summary()),
                cleanRequired(request.category()),
                cleanOptional(request.coverImageUrl()),
                request.content().trim(),
                cleanTags(request.tags()),
                request.featured(),
                request.sortOrder()
        );
        return DocsArticleResponse.from(article);
    }

    @Transactional
    public DocsArticleResponse publish(UUID id) {
        DocsArticleEntity article = find(id);
        article.publish();
        return DocsArticleResponse.from(article);
    }

    @Transactional
    public DocsArticleResponse unpublish(UUID id) {
        DocsArticleEntity article = find(id);
        article.unpublish();
        return DocsArticleResponse.from(article);
    }

    @Transactional
    public void delete(UUID id) {
        repository.delete(find(id));
    }

    private DocsArticleEntity find(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new UserException(UserErrorCode.DOCS_ARTICLE_NOT_FOUND));
    }

    private String resolveSlug(String requested, String title) {
        String source = requested == null || requested.isBlank() ? title : requested;
        String normalized = normalizeSlug(source);
        return normalized.isBlank() ? "guide-" + UUID.randomUUID().toString().substring(0, 8) : normalized;
    }

    private String normalizeSlug(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKD)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "")
                .replaceAll("-{2,}", "-");
    }

    private String normalizeSearch(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String cleanRequired(String value) {
        return value.trim();
    }

    private String cleanOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private List<String> cleanTags(List<String> tags) {
        if (tags == null) return List.of();
        return tags.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(tag -> !tag.isBlank())
                .distinct()
                .limit(12)
                .toList();
    }

    private PageRequest pageRequest(int page, int size, int maxSize) {
        int safePage = Math.max(page, 1) - 1;
        int safeSize = Math.max(1, Math.min(size, maxSize));
        return PageRequest.of(safePage, safeSize);
    }
}
