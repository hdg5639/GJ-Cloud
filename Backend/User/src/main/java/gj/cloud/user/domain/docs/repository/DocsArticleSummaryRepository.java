package gj.cloud.user.domain.docs.repository;

import gj.cloud.user.domain.docs.entity.DocsArticleSummaryEntity;
import gj.cloud.user.domain.docs.enums.DocsArticleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface DocsArticleSummaryRepository extends JpaRepository<DocsArticleSummaryEntity, UUID> {

    Page<DocsArticleSummaryEntity> findAllByStatusOrderByFeaturedDescSortOrderAscPublishedAtDesc(
            DocsArticleStatus status, Pageable pageable);

    Page<DocsArticleSummaryEntity> findAllByStatusAndCategoryIgnoreCaseOrderByFeaturedDescSortOrderAscPublishedAtDesc(
            DocsArticleStatus status, String category, Pageable pageable);

    Page<DocsArticleSummaryEntity> findAllByOrderByUpdatedAtDesc(Pageable pageable);

    Page<DocsArticleSummaryEntity> findAllByStatusOrderByUpdatedAtDesc(
            DocsArticleStatus status, Pageable pageable);

    List<DocsArticleSummaryEntity> findTop2ByStatusAndFeaturedTrueOrderBySortOrderAscPublishedAtDesc(
            DocsArticleStatus status);

    List<DocsArticleSummaryEntity> findTop100ByStatusAndCategoryIgnoreCaseOrderByFeaturedDescSortOrderAscPublishedAtDesc(
            DocsArticleStatus status, String category);

    long countByStatus(DocsArticleStatus status);

    @Query("select count(distinct article.category) from DocsArticleSummaryEntity article")
    long countDistinctCategories();

    @Query(value = """
            SELECT article.id, article.slug, article.title, article.summary, article.category,
                   article.cover_image_url, article.status, article.featured, article.sort_order,
                   article.view_count, article.published_at, article.created_at, article.updated_at
              FROM docs_articles article
              JOIN (
                    SELECT matched.id
                      FROM docs_articles matched
                     WHERE MATCH(matched.title, matched.summary, matched.category)
                           AGAINST (:query IN NATURAL LANGUAGE MODE)
                    UNION
                    SELECT tagged.article_id AS id
                      FROM docs_article_tags tagged
                     WHERE MATCH(tagged.tag) AGAINST (:query IN NATURAL LANGUAGE MODE)
              ) hit ON hit.id = article.id
             WHERE article.status = :status
               AND (:category = '' OR article.category = :category)
             ORDER BY article.featured DESC, article.sort_order ASC, article.published_at DESC
            """,
            countQuery = """
            SELECT COUNT(*)
              FROM docs_articles article
              JOIN (
                    SELECT matched.id
                      FROM docs_articles matched
                     WHERE MATCH(matched.title, matched.summary, matched.category)
                           AGAINST (:query IN NATURAL LANGUAGE MODE)
                    UNION
                    SELECT tagged.article_id AS id
                      FROM docs_article_tags tagged
                     WHERE MATCH(tagged.tag) AGAINST (:query IN NATURAL LANGUAGE MODE)
              ) hit ON hit.id = article.id
             WHERE article.status = :status
               AND (:category = '' OR article.category = :category)
            """, nativeQuery = true)
    Page<DocsArticleSummaryEntity> searchPublished(
            @Param("status") String status,
            @Param("category") String category,
            @Param("query") String query,
            Pageable pageable);

    @Query("""
            select distinct article
              from DocsArticleSummaryEntity article
              left join article.tags tag
             where article.status = :status
               and (:category = '' or lower(article.category) = lower(:category))
               and (
                    lower(article.title) like lower(concat('%', :query, '%'))
                    or lower(article.summary) like lower(concat('%', :query, '%'))
                    or lower(article.category) like lower(concat('%', :query, '%'))
                    or lower(tag) like lower(concat('%', :query, '%'))
               )
             order by article.featured desc, article.sortOrder asc, article.publishedAt desc
            """)
    Page<DocsArticleSummaryEntity> searchPublishedContains(
            @Param("status") DocsArticleStatus status,
            @Param("category") String category,
            @Param("query") String query,
            Pageable pageable);

    @Query(value = """
            SELECT article.id, article.slug, article.title, article.summary, article.category,
                   article.cover_image_url, article.status, article.featured, article.sort_order,
                   article.view_count, article.published_at, article.created_at, article.updated_at
              FROM docs_articles article
              JOIN (
                    SELECT matched.id
                      FROM docs_articles matched
                     WHERE MATCH(matched.title, matched.summary, matched.category)
                           AGAINST (:query IN NATURAL LANGUAGE MODE)
                    UNION
                    SELECT tagged.article_id AS id
                      FROM docs_article_tags tagged
                     WHERE MATCH(tagged.tag) AGAINST (:query IN NATURAL LANGUAGE MODE)
              ) hit ON hit.id = article.id
             WHERE (:status = '' OR article.status = :status)
             ORDER BY article.updated_at DESC
            """,
            countQuery = """
            SELECT COUNT(*)
              FROM docs_articles article
              JOIN (
                    SELECT matched.id
                      FROM docs_articles matched
                     WHERE MATCH(matched.title, matched.summary, matched.category)
                           AGAINST (:query IN NATURAL LANGUAGE MODE)
                    UNION
                    SELECT tagged.article_id AS id
                      FROM docs_article_tags tagged
                     WHERE MATCH(tagged.tag) AGAINST (:query IN NATURAL LANGUAGE MODE)
              ) hit ON hit.id = article.id
             WHERE (:status = '' OR article.status = :status)
            """, nativeQuery = true)
    Page<DocsArticleSummaryEntity> searchAdmin(
            @Param("status") String status,
            @Param("query") String query,
            Pageable pageable);

    @Query("""
            select distinct article
              from DocsArticleSummaryEntity article
              left join article.tags tag
             where (:status is null or article.status = :status)
               and (
                    lower(article.title) like lower(concat('%', :query, '%'))
                    or lower(article.summary) like lower(concat('%', :query, '%'))
                    or lower(article.category) like lower(concat('%', :query, '%'))
                    or lower(tag) like lower(concat('%', :query, '%'))
               )
             order by article.updatedAt desc
            """)
    Page<DocsArticleSummaryEntity> searchAdminContains(
            @Param("status") DocsArticleStatus status,
            @Param("query") String query,
            Pageable pageable);

    @Query("""
            select article.category as name, count(article.id) as articleCount
              from DocsArticleSummaryEntity article
             where article.status = :status
             group by article.category
             order by article.category asc
            """)
    List<DocsCategoryCountProjection> countByCategory(@Param("status") DocsArticleStatus status);
}
