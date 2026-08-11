SET SESSION cte_max_recursion_depth = 1000000;

CREATE TEMPORARY TABLE bench_numbers (n INT NOT NULL PRIMARY KEY) ENGINE=InnoDB;
INSERT INTO bench_numbers
WITH RECURSIVE sequence AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM sequence WHERE n < 1000000
)
SELECT n FROM sequence;

CREATE TEMPORARY TABLE bench_users LIKE users;
CREATE TEMPORARY TABLE bench_account_deletion_jobs LIKE account_deletion_jobs;
CREATE TEMPORARY TABLE bench_security_audit_logs LIKE security_audit_logs;

INSERT INTO bench_users (id, email, password, role, status, created_at, updated_at, deleted_at)
SELECT CONCAT('usr-', LPAD(n, 32, '0')),
       CONCAT('user', n, '@benchmark.invalid'),
       'benchmark-password-hash',
       IF(n % 100 = 0, 'ADMIN', 'USER'),
       CASE WHEN n % 20 = 0 THEN 'PENDING_VERIFICATION'
            WHEN n % 50 = 0 THEN 'DELETED'
            ELSE 'ACTIVE' END,
       NOW() - INTERVAL (100000 - n) SECOND,
       NOW() - INTERVAL (100000 - n) SECOND,
       IF(n % 50 = 0, NOW() - INTERVAL 31 DAY, NULL)
FROM bench_numbers WHERE n <= 100000;

INSERT INTO bench_security_audit_logs (
    id, occurred_at, actor_type, actor_id, action, target_type,
    target_id, result, ip, correlation_id, reason
)
SELECT CONCAT('aud-', LPAD(n, 32, '0')),
       NOW() - INTERVAL (1000000 - n) SECOND,
       'USER',
       CONCAT('usr-', LPAD(((n - 1) % 10000) + 1, 32, '0')),
       IF(n % 10 = 0, 'LOGIN_FAILED', 'LOGIN_SUCCEEDED'),
       'USER',
       CONCAT('usr-', LPAD(((n - 1) % 10000) + 1, 32, '0')),
       IF(n % 10 = 0, 'FAILURE', 'SUCCESS'),
       CONCAT('10.0.', (n DIV 256) % 256, '.', n % 256),
       CONCAT('cor-', LPAD(n, 32, '0')),
       IF(n % 10 = 0, 'BAD_CREDENTIALS', NULL)
FROM bench_numbers;

INSERT INTO bench_account_deletion_jobs (
    id, user_id, email, status, user_service_done, vm_service_done,
    attempt_count, created_at, updated_at
)
SELECT CONCAT('job-', LPAD(n, 32, '0')),
       CONCAT('usr-', LPAD(n, 32, '0')),
       CONCAT('user', n, '@benchmark.invalid'),
       CASE WHEN n % 5 = 0 THEN 'FAILED_RETRYABLE'
            WHEN n % 5 = 1 THEN 'PENDING'
            ELSE 'COMPLETED' END,
       n % 3 = 0,
       n % 4 = 0,
       n % 6,
       NOW() - INTERVAL (50000 - n) SECOND,
       NOW() - INTERVAL (50000 - n) SECOND
FROM bench_numbers WHERE n <= 50000;

ANALYZE TABLE bench_users, bench_security_audit_logs, bench_account_deletion_jobs;

SELECT '=== SYNTHETIC TABLE SIZES ===' AS section;
SELECT 'users' AS table_name, COUNT(*) AS row_count FROM bench_users
UNION ALL SELECT 'security_audit_logs', COUNT(*) FROM bench_security_audit_logs
UNION ALL SELECT 'account_deletion_jobs', COUNT(*) FROM bench_account_deletion_jobs;

SELECT '=== A1 LOGIN USER BY EMAIL ===' AS section;
EXPLAIN ANALYZE SELECT * FROM bench_users WHERE email = 'user100000@benchmark.invalid';

SELECT '=== A2 ACTIVE EMAIL EXISTENCE ===' AS section;
EXPLAIN ANALYZE SELECT EXISTS (
    SELECT 1 FROM bench_users
    WHERE email = 'user99999@benchmark.invalid' AND status <> 'DELETED'
);

SELECT '=== A3 EXPIRED PENDING USERS ===' AS section;
EXPLAIN ANALYZE SELECT id FROM bench_users
WHERE status = 'PENDING_VERIFICATION' AND created_at < NOW() - INTERVAL 1 DAY
ORDER BY created_at ASC LIMIT 1000;

SELECT '=== A4 EXPIRED DELETED USERS ===' AS section;
EXPLAIN ANALYZE SELECT id FROM bench_users
WHERE status = 'DELETED' AND deleted_at < NOW() - INTERVAL 30 DAY
ORDER BY deleted_at ASC LIMIT 1000;

SELECT '=== A5 RECENT SECURITY AUDIT LOGS ===' AS section;
EXPLAIN ANALYZE SELECT * FROM bench_security_audit_logs
ORDER BY occurred_at DESC LIMIT 20;

SELECT '=== A6 RECENT AUDIT LOGS BY ACTOR ===' AS section;
EXPLAIN ANALYZE SELECT * FROM bench_security_audit_logs
WHERE actor_id = 'usr-00000000000000000000000000000001'
ORDER BY occurred_at DESC LIMIT 20;

SELECT '=== A7 RETRYABLE ACCOUNT DELETION JOBS ===' AS section;
EXPLAIN ANALYZE SELECT * FROM bench_account_deletion_jobs
WHERE status = 'FAILED_RETRYABLE'
ORDER BY updated_at ASC LIMIT 100;
