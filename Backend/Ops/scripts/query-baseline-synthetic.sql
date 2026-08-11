\set ON_ERROR_STOP on
\pset pager off
\pset footer off
\timing on

\echo '=== BUILD ISOLATED SYNTHETIC DATA ==='
CREATE TEMP TABLE bench_deployment_targets
    (LIKE public.deployment_targets INCLUDING ALL);
CREATE TEMP TABLE bench_deployments
    (LIKE public.deployments INCLUDING ALL);
CREATE TEMP TABLE bench_deployment_events
    (LIKE public.deployment_events INCLUDING ALL);
CREATE TEMP TABLE bench_managed_preview_deployments
    (LIKE public.managed_preview_deployments INCLUDING ALL);

INSERT INTO bench_deployment_targets (
    id, vm_id, owner_user_id, owner_email, name, repository_url, branch,
    source_type, source_compose_ciphertext, auto_deploy_enabled, active,
    latest_requested_revision, latest_requested_at,
    created_at, updated_at
)
SELECT 'tgt-' || lpad(series::text, 32, '0'),
       'vm-' || lpad((((series - 1) % 10000) + 1)::text, 33, '0'),
       'user-' || (((series - 1) % 10000) + 1),
       'user' || (((series - 1) % 10000) + 1) || '@benchmark.invalid',
       'app-' || ((series - 1) / 10000),
       'https://example.invalid/repository-' || series || '.git',
       'main',
       CASE WHEN series % 3 = 0 THEN 'RAW_COMPOSE'
            WHEN series % 3 = 1 THEN 'AI_SPEC'
            ELSE 'TEMPLATE_SPEC' END,
       'benchmark-ciphertext',
       series % 4 = 0,
       series % 20 <> 0,
       CASE WHEN series % 4 = 0
            THEN CASE WHEN series % 8 = 0 THEN md5('pending-' || series)
                      ELSE md5(series::text) END
            ELSE NULL END,
       CASE WHEN series % 4 = 0 THEN now() - interval '30 days' ELSE NULL END,
       now() - (100000 - series) * interval '1 second',
       now() - (100000 - series) * interval '1 second'
FROM generate_series(1, 100000) AS series;

INSERT INTO bench_deployments (
    id, vm_id, deployment_target_id, trigger_type, requested_revision,
    status, source_type, source_compose_ciphertext, created_at, updated_at,
    deployed_at
)
SELECT 'dep-' || lpad(series::text, 32, '0'),
       'vm-' || lpad(((((series - 1) % 100000) % 10000) + 1)::text, 33, '0'),
       CASE WHEN series % 10 = 1 THEN NULL
            ELSE 'tgt-' || lpad((((series - 1) % 100000) + 1)::text, 32, '0') END,
       CASE WHEN series % 5 = 0 THEN 'GIT_PUSH' ELSE 'MANUAL' END,
       md5((((series - 1) % 200000) + 1)::text),
       CASE WHEN series % 100 = 0 THEN 'STOPPING'
            WHEN series % 10 = 0 THEN 'FAILED'
            ELSE 'SUCCEEDED' END,
       CASE WHEN series % 3 = 0 THEN 'RAW_COMPOSE'
            WHEN series % 3 = 1 THEN 'AI_SPEC'
            ELSE 'TEMPLATE_SPEC' END,
       'benchmark-ciphertext',
       now() - (500000 - series) * interval '1 second',
       now() - (500000 - series) * interval '1 second',
       CASE WHEN series % 10 = 0 THEN NULL
            ELSE now() - (500000 - series) * interval '1 second' END
FROM generate_series(1, 500000) AS series;

UPDATE bench_deployment_targets target
   SET latest_deployment_id = latest.id,
       latest_deployed_revision = latest.requested_revision
  FROM (
        SELECT DISTINCT ON (deployment_target_id)
               deployment_target_id, id, requested_revision
          FROM bench_deployments
         WHERE deployment_target_id IS NOT NULL
         ORDER BY deployment_target_id, created_at DESC
  ) latest
 WHERE target.id = latest.deployment_target_id
   AND substring(target.id from 34)::integer % 12 <> 0;

INSERT INTO bench_deployment_events (
    id, deployment_id, sequence, event_type, message, created_at
)
SELECT 'evt-' || lpad(series::text, 32, '0'),
       'dep-' || lpad((((series - 1) / 2) + 1)::text, 32, '0'),
       ((series - 1) % 2) + 1,
       CASE WHEN series % 2 = 0 THEN 'DONE' ELSE 'BUILD_LOG' END,
       'synthetic benchmark event',
       now() - (1000000 - series) * interval '500 milliseconds'
FROM generate_series(1, 1000000) AS series;

INSERT INTO bench_managed_preview_deployments (
    id, user_id, target_type, worker_id, container_name,
    compose_project_name, hostname, subdomain, internal_port, status,
    created_at, expires_at, updated_at
)
SELECT 'prv-' || lpad(series::text, 32, '0'),
       'user-' || (((series - 1) % 10000) + 1),
       'MANAGED',
       'wrk-00000000000000000000000000000001',
       'preview-' || series,
       'preview-' || series,
       'preview-' || series || '.benchmark.invalid',
       'preview-' || series,
       20000 + ((series - 1) % 10000),
       CASE WHEN series <= 10000
                THEN (ARRAY['ALLOCATED','QUEUED','BUILDING','RUNNING','FAILED'])[((series - 1) % 5) + 1]
            WHEN series % 2 = 0 THEN 'STOPPED'
            ELSE 'EXPIRED' END,
       now() - (100000 - series) * interval '1 minute',
       CASE WHEN series <= 5000 THEN now() - interval '1 hour'
            ELSE now() + interval '1 day' END,
       now()
FROM generate_series(1, 100000) AS series;

ANALYZE bench_deployment_targets;
ANALYZE bench_deployments;
ANALYZE bench_deployment_events;
ANALYZE bench_managed_preview_deployments;

\echo '=== SYNTHETIC TABLE SIZES ==='
SELECT 'deployment_targets' AS table_name, count(*) AS rows FROM bench_deployment_targets
UNION ALL SELECT 'deployments', count(*) FROM bench_deployments
UNION ALL SELECT 'deployment_events', count(*) FROM bench_deployment_events
UNION ALL SELECT 'managed_preview_deployments', count(*) FROM bench_managed_preview_deployments
ORDER BY table_name;

\echo '=== Q1 VM DEPLOYMENT HISTORY ==='
EXPLAIN (ANALYZE, BUFFERS, SUMMARY)
SELECT * FROM bench_deployments
WHERE vm_id = 'vm-' || lpad('1', 33, '0')
ORDER BY created_at DESC;

\echo '=== Q2 GLOBAL RECENT DEPLOYMENTS ==='
EXPLAIN (ANALYZE, BUFFERS, SUMMARY)
SELECT * FROM bench_deployments ORDER BY created_at DESC LIMIT 100;

\echo '=== Q3 STOPPING DEPLOYMENT RECOVERY ==='
EXPLAIN (ANALYZE, BUFFERS, SUMMARY)
SELECT * FROM bench_deployments WHERE status = 'STOPPING';

\echo '=== Q4 LEGACY LATEST SUCCEEDED DEPLOYMENT ==='
EXPLAIN (ANALYZE, BUFFERS, SUMMARY)
SELECT * FROM bench_deployments
WHERE vm_id = 'vm-' || lpad('1', 33, '0')
  AND deployment_target_id IS NULL
  AND status = 'SUCCEEDED'
ORDER BY created_at DESC LIMIT 1;

\echo '=== Q5 DUPLICATE REVISION CHECK ==='
EXPLAIN (ANALYZE, BUFFERS, SUMMARY)
SELECT EXISTS (
    SELECT 1 FROM bench_deployments
    WHERE deployment_target_id = 'tgt-' || lpad('2', 32, '0')
      AND requested_revision = md5('2')
      AND created_at >= now() - interval '30 days'
);

\echo '=== Q6 RECENT DEPLOYMENT TARGETS ==='
EXPLAIN (ANALYZE, BUFFERS, SUMMARY)
SELECT * FROM bench_deployment_targets ORDER BY updated_at DESC LIMIT 200;

\echo '=== Q7 ACTIVE TARGETS FOR VM ==='
EXPLAIN (ANALYZE, BUFFERS, SUMMARY)
SELECT * FROM bench_deployment_targets
WHERE vm_id = 'vm-' || lpad('1', 33, '0') AND active = TRUE
ORDER BY created_at ASC;

\echo '=== Q8 ADMIN LATEST EVENT PER DEPLOYMENT ==='
EXPLAIN (ANALYZE, BUFFERS, SUMMARY)
WITH recent_deployments AS MATERIALIZED (
    SELECT id FROM bench_deployments ORDER BY created_at DESC LIMIT 100
)
SELECT DISTINCT ON (event.deployment_id) event.* FROM bench_deployment_events event
WHERE event.deployment_id IN (SELECT id FROM recent_deployments)
ORDER BY event.deployment_id ASC, event.sequence DESC;

\echo '=== Q9 MANAGED PREVIEW FIRST AVAILABLE PORT ==='
EXPLAIN (ANALYZE, BUFFERS, SUMMARY)
SELECT candidate
FROM generate_series(20000, 29999) AS candidate
WHERE NOT EXISTS (
    SELECT 1 FROM bench_managed_preview_deployments preview
    WHERE preview.internal_port = candidate
      AND preview.status IN ('ALLOCATED', 'QUEUED', 'BUILDING', 'RUNNING', 'FAILED')
)
ORDER BY candidate
LIMIT 1;

\echo '=== Q10 MANAGED PREVIEW EXPIRY CLEANUP ==='
EXPLAIN (ANALYZE, BUFFERS, SUMMARY)
SELECT * FROM bench_managed_preview_deployments
WHERE expires_at < now()
  AND status IN ('ALLOCATED', 'QUEUED', 'BUILDING', 'RUNNING', 'FAILED');

\echo '=== Q11 PENDING AUTOMATIC TARGETS ONLY ==='
EXPLAIN (ANALYZE, BUFFERS, SUMMARY)
SELECT target.id, target.latest_requested_revision
FROM bench_deployment_targets target
WHERE target.active = TRUE
  AND target.auto_deploy_enabled = TRUE
  AND target.latest_requested_revision IS NOT NULL
  AND (
       target.latest_requested_at IS NULL
       OR NOT EXISTS (
           SELECT 1
           FROM bench_deployments deployment
           WHERE deployment.deployment_target_id = target.id
             AND deployment.requested_revision = target.latest_requested_revision
             AND deployment.created_at >= target.latest_requested_at
       )
  )
ORDER BY target.created_at ASC;

\echo '=== Q12 ACTIVE POINTER SYNC CANDIDATES (SET-BASED) ==='
EXPLAIN (ANALYZE, BUFFERS, SUMMARY)
SELECT latest.*
FROM (
      SELECT DISTINCT ON (deployment.deployment_target_id) deployment.*
      FROM bench_deployments deployment
      JOIN bench_deployment_targets target
        ON target.id = deployment.deployment_target_id
       AND target.active = TRUE
      ORDER BY deployment.deployment_target_id, deployment.created_at DESC
) latest
JOIN bench_deployment_targets target ON target.id = latest.deployment_target_id
WHERE (latest.status = 'SUCCEEDED'
       AND target.latest_deployment_id IS DISTINCT FROM latest.id)
   OR (latest.status = 'ROLLED_BACK'
       AND latest.previous_deployment_id IS NOT NULL
       AND target.latest_deployment_id IS DISTINCT FROM latest.previous_deployment_id);
