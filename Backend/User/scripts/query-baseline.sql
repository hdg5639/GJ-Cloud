SELECT '=== ENVIRONMENT ===' AS section;
SELECT NOW() AS measured_at, DATABASE() AS database_name, VERSION() AS mysql_version;

SELECT '=== TABLE STATISTICS ===' AS section;
SELECT table_name, table_rows AS estimated_rows,
       ROUND((data_length + index_length) / 1024, 1) AS total_kib
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
      'user_profiles', 'ssh_keys', 'upgrade_requests', 'docs_articles',
      'docs_article_tags', 'support_inquiries'
  )
ORDER BY data_length + index_length DESC;

SELECT '=== EXACT ROW COUNTS ===' AS section;
SELECT 'user_profiles' AS table_name, COUNT(*) AS row_count FROM user_profiles
UNION ALL SELECT 'ssh_keys', COUNT(*) FROM ssh_keys
UNION ALL SELECT 'upgrade_requests', COUNT(*) FROM upgrade_requests
UNION ALL SELECT 'docs_articles', COUNT(*) FROM docs_articles
UNION ALL SELECT 'docs_article_tags', COUNT(*) FROM docs_article_tags
UNION ALL SELECT 'support_inquiries', COUNT(*) FROM support_inquiries
ORDER BY table_name;

SELECT '=== CURRENT INDEXES ===' AS section;
SELECT table_name, index_name, non_unique,
       GROUP_CONCAT(column_name ORDER BY seq_in_index) AS columns_in_order
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name IN (
      'user_profiles', 'ssh_keys', 'upgrade_requests', 'docs_articles',
      'docs_article_tags', 'support_inquiries'
  )
GROUP BY table_name, index_name, non_unique
ORDER BY table_name, index_name;

START TRANSACTION READ ONLY;

SELECT '=== U1 PROFILE CONTAINS SEARCH ===' AS section;
EXPLAIN ANALYZE
SELECT profile.*
FROM user_profiles profile
CROSS JOIN (
    SELECT LEFT(COALESCE(NULLIF(nickname, ''), email), 2) AS term
    FROM user_profiles
    ORDER BY created_at DESC
    LIMIT 1
) seed
WHERE LOWER(profile.nickname) LIKE CONCAT('%', LOWER(seed.term), '%')
   OR LOWER(profile.email) LIKE CONCAT('%', LOWER(seed.term), '%')
LIMIT 10;

SELECT '=== U2 ADMIN USER LIST ===' AS section;
EXPLAIN ANALYZE
SELECT *
FROM user_profiles;

SELECT '=== U3 PUBLISHED DOCS ORDER ===' AS section;
EXPLAIN ANALYZE
SELECT *
FROM docs_articles
WHERE status = 'PUBLISHED'
ORDER BY featured DESC, sort_order ASC, published_at DESC;

SELECT '=== U4 ADMIN DOCS RECENTLY UPDATED ===' AS section;
EXPLAIN ANALYZE
SELECT *
FROM docs_articles
ORDER BY updated_at DESC;

SELECT '=== U5 USER SUPPORT HISTORY ===' AS section;
EXPLAIN ANALYZE
SELECT *
FROM support_inquiries
WHERE user_id = (
    SELECT user_id
    FROM (SELECT user_id FROM support_inquiries ORDER BY created_at DESC LIMIT 1) seed
)
ORDER BY created_at DESC
LIMIT 20;

SELECT '=== U6 SUPPORT STATUS QUEUE ===' AS section;
EXPLAIN ANALYZE
SELECT *
FROM support_inquiries
WHERE status = 'OPEN'
ORDER BY created_at DESC
LIMIT 20;

SELECT '=== U7 GLOBAL SUPPORT HISTORY ===' AS section;
EXPLAIN ANALYZE
SELECT *
FROM support_inquiries
ORDER BY created_at DESC
LIMIT 20;

SELECT '=== U8 UPGRADE STATUS QUEUE ===' AS section;
EXPLAIN ANALYZE
SELECT *
FROM upgrade_requests
WHERE status = 'PENDING'
ORDER BY created_at DESC
LIMIT 20;

SELECT '=== U9 UPGRADE REQUEST BY USER AND STATUS ===' AS section;
EXPLAIN ANALYZE
SELECT *
FROM upgrade_requests
WHERE user_id = (
    SELECT user_id
    FROM (SELECT user_id FROM upgrade_requests ORDER BY created_at DESC LIMIT 1) seed
)
  AND status = 'PENDING';

ROLLBACK;
