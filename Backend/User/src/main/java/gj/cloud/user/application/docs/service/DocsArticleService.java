package gj.cloud.user.application.docs.service;

import gj.cloud.user.application.docs.dto.*;
import gj.cloud.user.domain.docs.entity.DocsArticleEntity;
import gj.cloud.user.domain.docs.enums.DocsArticleStatus;
import gj.cloud.user.domain.docs.repository.DocsArticleRepository;
import gj.cloud.user.global.exception.UserException;
import gj.cloud.user.global.exception.enums.UserErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocsArticleService {

    private final DocsArticleRepository repository;

    @Transactional(readOnly = true)
    public List<DocsArticleSummaryResponse> listPublished(String query, String category) {
        String normalizedQuery = normalizeSearch(query);
        String normalizedCategory = normalizeSearch(category);
        return repository.findAllByStatusOrderByFeaturedDescSortOrderAscPublishedAtDesc(DocsArticleStatus.PUBLISHED)
                .stream()
                .filter(matchesCategory(normalizedCategory))
                .filter(matchesQuery(normalizedQuery))
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

    @Transactional(readOnly = true)
    public List<DocsCategoryResponse> listCategories() {
        Map<String, Long> counts = repository
                .findAllByStatusOrderByFeaturedDescSortOrderAscPublishedAtDesc(DocsArticleStatus.PUBLISHED)
                .stream()
                .collect(Collectors.groupingBy(DocsArticleEntity::getCategory, TreeMap::new, Collectors.counting()));
        return counts.entrySet().stream()
                .map(entry -> new DocsCategoryResponse(entry.getKey(), entry.getValue()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DocsArticleSummaryResponse> listAdmin() {
        return repository.findAllByOrderByUpdatedAtDesc().stream()
                .map(DocsArticleSummaryResponse::from)
                .toList();
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

    private Predicate<DocsArticleEntity> matchesCategory(String category) {
        if (category.isBlank()) return ignored -> true;
        return article -> normalizeSearch(article.getCategory()).equals(category);
    }

    private Predicate<DocsArticleEntity> matchesQuery(String query) {
        if (query.isBlank()) return ignored -> true;
        return article -> {
            String searchable = String.join(" ",
                    article.getTitle(), article.getSummary(), article.getCategory(), String.join(" ", article.getTags()));
            return normalizeSearch(searchable).contains(query);
        };
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
}
