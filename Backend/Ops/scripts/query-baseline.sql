\set ON_ERROR_STOP on
\pset pager off
\pset footer off
\timing on

BEGIN TRANSACTION READ ONLY;

\echo '=== ENVIRONMENT ==='
SELECT now() AS measured_at,
       current_database() AS database_name,
       current_setting('server_version') AS postgres_version;

\echo '=== TABLE STATISTICS ==='
SELECT relname AS table_name,
       n_live_tup AS estimated_rows,
       seq_scan,
       idx_scan,
       pg_size_pretty(pg_total_relation_size(relid)) AS total_size
FROM pg_stat_user_tables
WHERE relname IN (
    'deployment_targets',
    'deployments',
    'deployment_events',
    'managed_preview_deployments',
    'regression_suites',
    'regression_suite_runs',
    'scenario_executions',
    'db_backups'
)
ORDER BY pg_total_relation_size(relid) DESC;

\echo '=== EXACT ROW COUNTS ==='
SELECT 'deployment_targets' AS table_name, count(*) AS row_count FROM deployment_targets
UNION ALL SELECT 'deployments', count(*) FROM deployments
UNION ALL SELECT 'deployment_events', count(*) FROM deployment_events
UNION ALL SELECT 'managed_preview_deployments', count(*) FROM managed_preview_deployments
UNION ALL SELECT 'regression_suites', count(*) FROM regression_suites
UNION ALL SELECT 'regression_suite_runs', count(*) FROM regression_suite_runs
UNION ALL SELECT 'scenario_executions', count(*) FROM scenario_executions
UNION ALL SELECT 'db_backups', count(*) FROM db_backups
ORDER BY table_name;

\echo '=== CURRENT INDEXES ==='
SELECT tablename, indexname, indexdef
FROM pg_indexes
WHERE schemaname = 'public'
  AND tablename IN (
      'deployment_targets',
      'deployments',
      'deployment_events',
      'managed_preview_deployments',
      'regression_suites',
      'regression_suite_runs',
      'scenario_executions',
      'db_backups'
  )
ORDER BY tablename, indexname;

\echo '=== Q1 VM DEPLOYMENT HISTORY ==='
EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS, SUMMARY)
SELECT *
FROM deployments
WHERE vm_id = (
    SELECT vm_id
    FROM deployments
    GROUP BY vm_id
    ORDER BY count(*) DESC
    LIMIT 1
)
ORDER BY created_at DESC;

\echo '=== Q2 GLOBAL RECENT DEPLOYMENTS ==='
EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS, SUMMARY)
SELECT *
FROM deployments
ORDER BY created_at DESC
LIMIT 100;

\echo '=== Q3 STOPPING DEPLOYMENT RECOVERY ==='
EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS, SUMMARY)
SELECT *
FROM deployments
WHERE status = 'STOPPING';

\echo '=== Q4 LEGACY LATEST SUCCEEDED DEPLOYMENT ==='
EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS, SUMMARY)
SELECT *
FROM deployments
WHERE vm_id = (
    SELECT vm_id
    FROM deployments
    GROUP BY vm_id
    ORDER BY count(*) DESC
    LIMIT 1
)
  AND deployment_target_id IS NULL
  AND status = 'SUCCEEDED'
ORDER BY created_at DESC
LIMIT 1;

\echo '=== Q5 DUPLICATE REVISION CHECK ==='
EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS, SUMMARY)
WITH seed AS MATERIALIZED (
    SELECT deployment_target_id, requested_revision, created_at
    FROM deployments
    WHERE deployment_target_id IS NOT NULL
      AND requested_revision IS NOT NULL
    ORDER BY created_at DESC
    LIMIT 1
)
SELECT EXISTS (
    SELECT 1
    FROM deployments deployment
    CROSS JOIN seed
    WHERE deployment.deployment_target_id = seed.deployment_target_id
      AND deployment.requested_revision = seed.requested_revision
      AND deployment.created_at >= seed.created_at - INTERVAL '1 day'
);

\echo '=== Q6 RECENT DEPLOYMENT TARGETS ==='
EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS, SUMMARY)
SELECT *
FROM deployment_targets
ORDER BY updated_at DESC
LIMIT 200;

\echo '=== Q7 ACTIVE TARGETS FOR VM ==='
EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS, SUMMARY)
SELECT *
FROM deployment_targets
WHERE vm_id = (
    SELECT vm_id
    FROM deployment_targets
    GROUP BY vm_id
    ORDER BY count(*) DESC
    LIMIT 1
)
  AND active = TRUE
ORDER BY created_at ASC;

\echo '=== Q8 ADMIN LATEST EVENT INPUT ==='
EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS, SUMMARY)
WITH recent_deployments AS MATERIALIZED (
    SELECT id
    FROM deployments
    ORDER BY created_at DESC
    LIMIT 100
)
SELECT event.*
FROM deployment_events event
WHERE event.deployment_id IN (SELECT id FROM recent_deployments)
ORDER BY event.deployment_id ASC, event.sequence DESC;

\echo '=== Q9 MANAGED PREVIEW OCCUPYING PORTS ==='
EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS, SUMMARY)
SELECT *
FROM managed_preview_deployments
WHERE status IN ('ALLOCATED', 'QUEUED', 'BUILDING', 'RUNNING', 'FAILED');

\echo '=== Q10 MANAGED PREVIEW EXPIRY CLEANUP ==='
EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS, SUMMARY)
SELECT *
FROM managed_preview_deployments
WHERE expires_at < now()
  AND status IN ('ALLOCATED', 'QUEUED', 'BUILDING', 'RUNNING', 'FAILED');

ROLLBACK;
