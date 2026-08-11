SET SESSION cte_max_recursion_depth = 100000;
SET NAMES utf8mb4;

-- FULLTEXT는 MySQL InnoDB TEMPORARY TABLE에서 지원되지 않는다. 충돌 가능성이 없는
-- 전용 이름의 일반 benchmark table을 사용하고 정상/오류 여부와 무관하게 재실행할 수 있게
-- 시작과 끝에서 이 두 table만 명시적으로 정리한다. 서비스 table은 참조하지 않는다.
DROP TABLE IF EXISTS bench_docs_search_tags;
DROP TABLE IF EXISTS bench_docs_search_articles;

CREATE TABLE bench_docs_search_articles LIKE docs_articles;
CREATE TABLE bench_docs_search_tags LIKE docs_article_tags;

INSERT INTO bench_docs_search_articles (
    id, slug, title, summary, category, content, status, featured,
    sort_order, view_count, author_id, published_at, created_at, updated_at
)
WITH RECURSIVE sequence AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM sequence WHERE n < 100000
)
SELECT UNHEX(LPAD(HEX(n), 32, '0')),
       CONCAT('docs-search-benchmark-', n),
       IF(n % 1000 = 1,
          CONCAT('인스턴스 생성 가이드 ', n),
          CONCAT('사용 설명서 ', n)),
       IF(n % 500 = 2, '자동 배포와 프리뷰 사용 방법', '합성 성능 측정 문서'),
       CONCAT('카테고리-', n % 20),
       REPEAT('본문 ', 256),
       IF(n % 5 = 0, 'DRAFT', 'PUBLISHED'),
       n % 100 = 0,
       n % 1000,
       n * 3,
       'benchmark-author',
       IF(n % 5 = 0, NULL, NOW(6) - INTERVAL (100000 - n) SECOND),
       NOW(6) - INTERVAL (100000 - n) SECOND,
       NOW(6) - INTERVAL (100000 - n) SECOND
FROM sequence;

INSERT INTO bench_docs_search_tags (article_id, tag, tag_order)
SELECT id,
       IF(MOD(CONV(HEX(id), 16, 10), 2000) = 0, '자동배포', '사용법'),
       0
FROM bench_docs_search_articles;

ANALYZE TABLE bench_docs_search_articles, bench_docs_search_tags;

SELECT '=== D1 PUBLISHED DOCS PAGE ===' AS section;
EXPLAIN ANALYZE
SELECT article.id, article.slug, article.title, article.summary, article.category,
       article.cover_image_url, article.status, article.featured, article.sort_order,
       article.view_count, article.published_at, article.created_at, article.updated_at
FROM bench_docs_search_articles article
WHERE article.status = 'PUBLISHED'
ORDER BY article.featured DESC, article.sort_order ASC, article.published_at DESC
LIMIT 18;

SELECT '=== D2 PUBLISHED DOCS COUNT ===' AS section;
EXPLAIN ANALYZE
SELECT COUNT(*) FROM bench_docs_search_articles WHERE status = 'PUBLISHED';

SELECT '=== D3 FULLTEXT SEARCH PAGE ===' AS section;
EXPLAIN ANALYZE
SELECT article.id, article.slug, article.title, article.summary, article.category,
       article.cover_image_url, article.status, article.featured, article.sort_order,
       article.view_count, article.published_at, article.created_at, article.updated_at
FROM bench_docs_search_articles article
JOIN (
    SELECT matched.id
    FROM bench_docs_search_articles matched
    WHERE MATCH(matched.title, matched.summary, matched.category)
          AGAINST ('인스턴스' IN NATURAL LANGUAGE MODE)
    UNION
    SELECT tagged.article_id AS id
    FROM bench_docs_search_tags tagged
    WHERE MATCH(tagged.tag) AGAINST ('인스턴스' IN NATURAL LANGUAGE MODE)
) hit ON hit.id = article.id
WHERE article.status = 'PUBLISHED'
ORDER BY article.featured DESC, article.sort_order ASC, article.published_at DESC
LIMIT 18;

SELECT '=== D4 FULLTEXT SEARCH COUNT ===' AS section;
EXPLAIN ANALYZE
SELECT COUNT(*)
FROM bench_docs_search_articles article
JOIN (
    SELECT matched.id
    FROM bench_docs_search_articles matched
    WHERE MATCH(matched.title, matched.summary, matched.category)
          AGAINST ('인스턴스' IN NATURAL LANGUAGE MODE)
    UNION
    SELECT tagged.article_id AS id
    FROM bench_docs_search_tags tagged
    WHERE MATCH(tagged.tag) AGAINST ('인스턴스' IN NATURAL LANGUAGE MODE)
) hit ON hit.id = article.id
WHERE article.status = 'PUBLISHED';

SELECT '=== D5 ADMIN DOCS PAGE ===' AS section;
EXPLAIN ANALYZE
SELECT article.id, article.slug, article.title, article.summary, article.category,
       article.cover_image_url, article.status, article.featured, article.sort_order,
       article.view_count, article.published_at, article.created_at, article.updated_at
FROM bench_docs_search_articles article
ORDER BY article.updated_at DESC
LIMIT 20;

DROP TABLE bench_docs_search_tags;
DROP TABLE bench_docs_search_articles;
