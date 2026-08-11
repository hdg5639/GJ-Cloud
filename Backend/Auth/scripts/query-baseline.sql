SELECT '=== ENVIRONMENT ===' AS section;
SELECT NOW() AS measured_at, DATABASE() AS database_name, VERSION() AS mysql_version;

SELECT '=== TABLE STATISTICS ===' AS section;
SELECT table_name, table_rows AS estimated_rows,
       ROUND((data_length + index_length) / 1024, 1) AS total_kib
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('users', 'account_deletion_jobs', 'security_audit_logs')
ORDER BY data_length + index_length DESC;

SELECT '=== EXACT ROW COUNTS ===' AS section;
SELECT 'users' AS table_name, COUNT(*) AS row_count FROM users
UNION ALL SELECT 'account_deletion_jobs', COUNT(*) FROM account_deletion_jobs
UNION ALL SELECT 'security_audit_logs', COUNT(*) FROM security_audit_logs
ORDER BY table_name;

SELECT '=== CURRENT INDEXES ===' AS section;
SELECT table_name, index_name, non_unique,
       GROUP_CONCAT(column_name ORDER BY seq_in_index) AS columns_in_order
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name IN ('users', 'account_deletion_jobs', 'security_audit_logs')
GROUP BY table_name, index_name, non_unique
ORDER BY table_name, index_name;

START TRANSACTION READ ONLY;

SELECT '=== A1 LOGIN USER BY EMAIL ===' AS section;
EXPLAIN ANALYZE
SELECT *
FROM users
WHERE email = (SELECT email FROM (SELECT email FROM users ORDER BY created_at DESC LIMIT 1) seed);

SELECT '=== A2 ACTIVE EMAIL EXISTENCE ===' AS section;
EXPLAIN ANALYZE
SELECT EXISTS (
    SELECT 1
    FROM users
    WHERE email = (SELECT email FROM (SELECT email FROM users ORDER BY created_at DESC LIMIT 1) seed)
      AND status <> 'DELETED'
);

SELECT '=== A3 EXPIRED PENDING USERS ===' AS section;
EXPLAIN ANALYZE
SELECT id
FROM users
WHERE status = 'PENDING_VERIFICATION'
  AND created_at < NOW() - INTERVAL 1 DAY;

SELECT '=== A4 EXPIRED DELETED USERS ===' AS section;
EXPLAIN ANALYZE
SELECT id
FROM users
WHERE status = 'DELETED'
  AND deleted_at < NOW() - INTERVAL 30 DAY;

SELECT '=== A5 RECENT SECURITY AUDIT LOGS ===' AS section;
EXPLAIN ANALYZE
SELECT *
FROM security_audit_logs
ORDER BY occurred_at DESC
LIMIT 20;

SELECT '=== A6 RECENT AUDIT LOGS BY ACTOR ===' AS section;
EXPLAIN ANALYZE
SELECT *
FROM security_audit_logs
WHERE actor_id = (
    SELECT actor_id
    FROM (SELECT actor_id FROM security_audit_logs WHERE actor_id IS NOT NULL ORDER BY occurred_at DESC LIMIT 1) seed
)
ORDER BY occurred_at DESC
LIMIT 20;

SELECT '=== A7 RETRYABLE ACCOUNT DELETION JOBS ===' AS section;
EXPLAIN ANALYZE
SELECT *
FROM account_deletion_jobs
WHERE status = 'FAILED_RETRYABLE';

ROLLBACK;
