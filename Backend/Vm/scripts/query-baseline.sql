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
    'vms', 'vm_ports', 'vm_port_access_emails', 'vm_ssh_access_emails',
    'organizations', 'organization_members', 'organization_vms',
    'collaboration_items', 'collaboration_tags'
)
ORDER BY pg_total_relation_size(relid) DESC;

\echo '=== EXACT ROW COUNTS ==='
SELECT 'vms' AS table_name, count(*) AS row_count FROM vms
UNION ALL SELECT 'vm_ports', count(*) FROM vm_ports
UNION ALL SELECT 'vm_port_access_emails', count(*) FROM vm_port_access_emails
UNION ALL SELECT 'vm_ssh_access_emails', count(*) FROM vm_ssh_access_emails
UNION ALL SELECT 'organizations', count(*) FROM organizations
UNION ALL SELECT 'organization_members', count(*) FROM organization_members
UNION ALL SELECT 'organization_vms', count(*) FROM organization_vms
UNION ALL SELECT 'collaboration_items', count(*) FROM collaboration_items
UNION ALL SELECT 'collaboration_tags', count(*) FROM collaboration_tags
ORDER BY table_name;

\echo '=== CURRENT INDEXES ==='
SELECT tablename, indexname, indexdef
FROM pg_indexes
WHERE schemaname = 'public'
  AND tablename IN (
      'vms', 'vm_ports', 'vm_port_access_emails', 'vm_ssh_access_emails',
      'organizations', 'organization_members', 'organization_vms',
      'collaboration_items', 'collaboration_tags'
  )
ORDER BY tablename, indexname;

\echo '=== V1 ACTIVE USER VM LIST ==='
EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS, SUMMARY)
SELECT *
FROM vms
WHERE user_id = (
    SELECT user_id FROM vms ORDER BY created_at DESC LIMIT 1
)
  AND deleted_at IS NULL;

\echo '=== V2 ACTIVE PROXMOX VMIDS ==='
EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS, SUMMARY)
SELECT vmid
FROM vms
WHERE vmid IS NOT NULL
  AND deleted_at IS NULL;

\echo '=== V3 USER PLAN VM COUNT ==='
EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS, SUMMARY)
SELECT count(*)
FROM vms
WHERE user_id = (
    SELECT user_id FROM vms ORDER BY created_at DESC LIMIT 1
)
  AND deleted_at IS NULL
  AND status NOT IN ('PENDING', 'FAILED', 'DELETED')
  AND plan_type = 'FREE';

\echo '=== V4 VM PORT LIST ==='
EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS, SUMMARY)
SELECT *
FROM vm_ports
WHERE vm_id = (
    SELECT vm_id FROM vm_ports GROUP BY vm_id ORDER BY count(*) DESC LIMIT 1
);

\echo '=== V5 VM PORT COLLISION COUNT ==='
EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS, SUMMARY)
SELECT count(*)
FROM vm_ports
WHERE vm_id = (
    SELECT vm_id FROM vm_ports ORDER BY created_at DESC LIMIT 1
)
  AND port = (
    SELECT port FROM vm_ports ORDER BY created_at DESC LIMIT 1
);

\echo '=== V6 ORGANIZATIONS BY MEMBER EMAIL ==='
EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS, SUMMARY)
SELECT organization.*
FROM organizations organization
JOIN organization_members member ON organization.id = member.organization_id
WHERE member.email = (
    SELECT email FROM organization_members ORDER BY invited_at DESC LIMIT 1
)
  AND member.status = 'ACCEPTED';

\echo '=== V7 COLLABORATION SCOPE ORDER ==='
EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS, SUMMARY)
SELECT *
FROM collaboration_items
WHERE scope_type = (
    SELECT scope_type FROM collaboration_items ORDER BY created_at DESC LIMIT 1
)
  AND scope_id = (
    SELECT scope_id FROM collaboration_items ORDER BY created_at DESC LIMIT 1
)
ORDER BY pinned DESC, created_at DESC;

\echo '=== V8 COLLABORATION SCOPE AND TYPE ORDER ==='
EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS, SUMMARY)
SELECT *
FROM collaboration_items
WHERE scope_type = (
    SELECT scope_type FROM collaboration_items ORDER BY created_at DESC LIMIT 1
)
  AND scope_id = (
    SELECT scope_id FROM collaboration_items ORDER BY created_at DESC LIMIT 1
)
  AND type = (
    SELECT type FROM collaboration_items ORDER BY created_at DESC LIMIT 1
)
ORDER BY pinned DESC, created_at DESC;

\echo '=== V9 TAG PREFIX SEARCH ==='
EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS, SUMMARY)
SELECT tag.*
FROM collaboration_tags tag
CROSS JOIN (
    SELECT scope_type, scope_id, left(name, 2) AS prefix
    FROM collaboration_tags
    ORDER BY last_used_at DESC
    LIMIT 1
) seed
WHERE tag.scope_type = seed.scope_type
  AND tag.scope_id = seed.scope_id
  AND tag.name ILIKE seed.prefix || '%'
ORDER BY tag.usage_count DESC, tag.last_used_at DESC
LIMIT 8;

ROLLBACK;
