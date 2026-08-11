SET SESSION cte_max_recursion_depth = 1000000;

CREATE TEMPORARY TABLE bench_numbers (n INT NOT NULL PRIMARY KEY) ENGINE=InnoDB;
INSERT INTO bench_numbers
WITH RECURSIVE sequence AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM sequence WHERE n < 1000000
)
SELECT n FROM sequence;

CREATE TEMPORARY TABLE bench_user_profiles LIKE user_profiles;
CREATE TEMPORARY TABLE bench_upgrade_requests LIKE upgrade_requests;
-- MySQL은 FULLTEXT index가 있는 InnoDB 테이블을 TEMPORARY TABLE LIKE로 복제할 수 없다.
-- 컬럼을 복제한 뒤 정렬/필터 측정에 필요한 B-tree index만 명시적으로 구성한다.
CREATE TEMPORARY TABLE bench_docs_articles AS
SELECT * FROM docs_articles WHERE 1 = 0;
ALTER TABLE bench_docs_articles
    ADD PRIMARY KEY (id),
    ADD UNIQUE INDEX uq_bench_docs_slug (slug),
    ADD INDEX idx_bench_docs_published_order
        (status, featured DESC, sort_order ASC, published_at DESC),
    ADD INDEX idx_bench_docs_updated_at (updated_at DESC),
    ADD INDEX idx_bench_docs_category_order
        (status, category, featured DESC, sort_order ASC, published_at DESC);
CREATE TEMPORARY TABLE bench_support_inquiries LIKE support_inquiries;

INSERT INTO bench_user_profiles (
    user_id, email, nickname, plan_type, suspended, created_at, updated_at
)
SELECT CONCAT('usr-', LPAD(n, 32, '0')),
       CONCAT('user', n, '@benchmark.invalid'),
       CONCAT('member-', LPAD(n, 6, '0')),
       IF(n % 10 = 0, 'PRO', 'FREE'),
       n % 1000 = 0,
       NOW() - INTERVAL (100000 - n) SECOND,
       NOW() - INTERVAL (100000 - n) SECOND
FROM bench_numbers WHERE n <= 100000;

INSERT INTO bench_upgrade_requests (
    id, user_id, type, target_plan_type, status, reason, created_at
)
SELECT UNHEX(LPAD(HEX(n), 32, '0')),
       CONCAT('usr-', LPAD(n, 32, '0')),
       'PLAN_CHANGE', 'PRO',
       CASE WHEN n % 5 = 0 THEN 'PENDING'
            WHEN n % 5 = 1 THEN 'REJECTED'
            ELSE 'APPROVED' END,
       'synthetic benchmark',
       NOW(6) - INTERVAL (100000 - n) SECOND
FROM bench_numbers WHERE n <= 100000;

INSERT INTO bench_docs_articles (
    id, slug, title, summary, category, content, status, featured,
    sort_order, view_count, author_id, published_at, created_at, updated_at
)
SELECT UNHEX(LPAD(HEX(n), 32, '0')),
       CONCAT('benchmark-article-', n),
       CONCAT('Benchmark article ', n),
       'Synthetic benchmark summary',
       CONCAT('category-', n % 20),
       'Synthetic benchmark content',
       IF(n % 5 = 0, 'DRAFT', 'PUBLISHED'),
       n % 100 = 0,
       n % 1000,
       n * 3,
       'benchmark-author',
       IF(n % 5 = 0, NULL, NOW(6) - INTERVAL (100000 - n) SECOND),
       NOW(6) - INTERVAL (100000 - n) SECOND,
       NOW(6) - INTERVAL (100000 - n) SECOND
FROM bench_numbers WHERE n <= 100000;

INSERT INTO bench_support_inquiries (
    id, user_id, requester_email, category, title, content,
    status, created_at, updated_at
)
SELECT UNHEX(LPAD(HEX(n + 1000000), 32, '0')),
       CONCAT('usr-', LPAD(((n - 1) % 100000) + 1, 32, '0')),
       CONCAT('user', ((n - 1) % 100000) + 1, '@benchmark.invalid'),
       CASE WHEN n % 3 = 0 THEN 'TECHNICAL'
            WHEN n % 3 = 1 THEN 'ACCOUNT'
            ELSE 'PLAN' END,
       CONCAT('Benchmark inquiry ', n),
       'Synthetic benchmark inquiry',
       CASE WHEN n % 5 = 0 THEN 'OPEN'
            WHEN n % 5 = 1 THEN 'ANSWERED'
            ELSE 'CLOSED' END,
       NOW(6) - INTERVAL (500000 - n) SECOND,
       NOW(6) - INTERVAL (500000 - n) SECOND
FROM bench_numbers WHERE n <= 500000;

ANALYZE TABLE bench_user_profiles, bench_upgrade_requests,
              bench_docs_articles, bench_support_inquiries;

SELECT '=== SYNTHETIC TABLE SIZES ===' AS section;
SELECT 'user_profiles' AS table_name, COUNT(*) AS row_count FROM bench_user_profiles
UNION ALL SELECT 'upgrade_requests', COUNT(*) FROM bench_upgrade_requests
UNION ALL SELECT 'docs_articles', COUNT(*) FROM bench_docs_articles
UNION ALL SELECT 'support_inquiries', COUNT(*) FROM bench_support_inquiries;

SELECT '=== U1A PROFILE PREFIX SEARCH ===' AS section;
EXPLAIN ANALYZE SELECT * FROM bench_user_profiles
WHERE nickname LIKE 'member-099%'
   OR email LIKE 'user999%'
ORDER BY (email = 'user99999@benchmark.invalid') DESC,
         (nickname = 'member-099999') DESC,
         created_at DESC
LIMIT 10;

SELECT '=== U1B PROFILE CONTAINS FALLBACK ===' AS section;
EXPLAIN ANALYZE SELECT * FROM bench_user_profiles
WHERE LOWER(nickname) LIKE '%99999%' OR LOWER(email) LIKE '%99999%'
LIMIT 10;

SELECT '=== U2 ADMIN USER PAGE ===' AS section;
EXPLAIN ANALYZE SELECT * FROM bench_user_profiles
ORDER BY created_at DESC LIMIT 50;

SELECT '=== U3 PUBLISHED DOCS PAGE ===' AS section;
EXPLAIN ANALYZE SELECT * FROM bench_docs_articles
WHERE status = 'PUBLISHED'
ORDER BY featured DESC, sort_order ASC, published_at DESC
LIMIT 18;

SELECT '=== U4 ADMIN DOCS PAGE ===' AS section;
EXPLAIN ANALYZE SELECT * FROM bench_docs_articles ORDER BY updated_at DESC LIMIT 20;

SELECT '=== U5 USER SUPPORT HISTORY ===' AS section;
EXPLAIN ANALYZE SELECT * FROM bench_support_inquiries
WHERE user_id = 'usr-00000000000000000000000000000001'
ORDER BY created_at DESC LIMIT 20;

SELECT '=== U6 SUPPORT STATUS QUEUE ===' AS section;
EXPLAIN ANALYZE SELECT * FROM bench_support_inquiries
WHERE status = 'OPEN' ORDER BY created_at DESC LIMIT 20;

SELECT '=== U7 GLOBAL SUPPORT HISTORY ===' AS section;
EXPLAIN ANALYZE SELECT * FROM bench_support_inquiries
ORDER BY created_at DESC LIMIT 20;

SELECT '=== U8 UPGRADE STATUS QUEUE ===' AS section;
EXPLAIN ANALYZE SELECT * FROM bench_upgrade_requests
WHERE status = 'PENDING' ORDER BY created_at DESC LIMIT 20;

SELECT '=== U9 UPGRADE REQUEST BY USER AND STATUS ===' AS section;
EXPLAIN ANALYZE SELECT * FROM bench_upgrade_requests
WHERE user_id = 'usr-00000000000000000000000000000005'
  AND status = 'PENDING';
