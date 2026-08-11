\set ON_ERROR_STOP on
\pset pager off
\pset footer off
\timing on

\echo '=== BUILD ISOLATED SYNTHETIC DATA ==='
CREATE TEMP TABLE bench_vms (LIKE public.vms INCLUDING ALL);
CREATE TEMP TABLE bench_vm_ports (LIKE public.vm_ports INCLUDING ALL);
CREATE TEMP TABLE bench_organizations (LIKE public.organizations INCLUDING ALL);
CREATE TEMP TABLE bench_organization_members
    (LIKE public.organization_members INCLUDING ALL);
CREATE TEMP TABLE bench_collaboration_items
    (LIKE public.collaboration_items INCLUDING ALL);
CREATE TEMP TABLE bench_collaboration_tags
    (LIKE public.collaboration_tags INCLUDING ALL);

INSERT INTO bench_vms (
    id, user_id, vmid, name, plan_type, status, ssh_key_id,
    internal_ip, created_at, updated_at, deleted_at, subdomain, disk_size_gb
)
SELECT ('00000000-0000-0000-0000-' || lpad(series::text, 12, '0'))::uuid,
       'usr-' || lpad((((series - 1) % 10000) + 1)::text, 32, '0'),
       100000 + series,
       'benchmark-vm-' || series,
       CASE WHEN series % 10 = 0 THEN 'PRO' ELSE 'FREE' END,
       CASE WHEN series % 20 = 0 THEN 'STOPPED'
            WHEN series % 50 = 0 THEN 'FAILED'
            ELSE 'RUNNING' END,
       'key-' || lpad(series::text, 32, '0'),
       '10.' || ((series / 65536) % 256) || '.' || ((series / 256) % 256) || '.' || (series % 256),
       now() - (100000 - series) * interval '1 second',
       now() - (100000 - series) * interval '1 second',
       CASE WHEN series % 100 = 0 THEN now() ELSE NULL END,
       'vm-' || series,
       CASE WHEN series % 10 = 0 THEN 100 ELSE 40 END
FROM generate_series(1, 100000) AS series;

INSERT INTO bench_vm_ports (
    id, vm_id, port, protocol, visibility, nickname, subdomain,
    created_at, deployment_id, deployment_app_id, linked_deployment_target_id
)
SELECT md5('port-' || series)::uuid,
       ('00000000-0000-0000-0000-' || lpad((((series - 1) % 100000) + 1)::text, 12, '0'))::uuid,
       8000 + (series % 1000),
       CASE WHEN series % 5 = 0 THEN 'TCP' ELSE 'HTTP' END,
       CASE WHEN series % 4 = 0 THEN 'PRIVATE' ELSE 'PUBLIC' END,
       'port-' || ((series - 1) / 100000),
       'port-' || series,
       now() - (500000 - series) * interval '1 second',
       CASE WHEN series % 3 = 0 THEN md5(series::text) ELSE NULL END,
       CASE WHEN series % 3 = 0 THEN md5(('app-' || series)) ELSE NULL END,
       CASE WHEN series % 4 = 0 THEN md5(('target-' || series)) ELSE NULL END
FROM generate_series(1, 500000) AS series;

INSERT INTO bench_organizations (id, name, owner_id, created_at)
SELECT md5('org-' || series)::uuid,
       'Benchmark organization ' || series,
       'usr-' || lpad(series::text, 32, '0'),
       now() - (10000 - series) * interval '1 minute'
FROM generate_series(1, 10000) AS series;

INSERT INTO bench_organization_members (
    id, organization_id, email, user_id, role, status, invited_at, joined_at
)
SELECT md5('member-' || series)::uuid,
       md5('org-' || (((series - 1) / 10) + 1))::uuid,
       'member' || (((series - 1) % 10000) + 1) || '@benchmark.invalid',
       'usr-' || lpad((((series - 1) % 10000) + 1)::text, 32, '0'),
       CASE WHEN series % 10 = 0 THEN 'ADMIN' ELSE 'MEMBER' END,
       CASE WHEN series % 20 = 0 THEN 'PENDING' ELSE 'ACCEPTED' END,
       now() - (100000 - series) * interval '1 second',
       CASE WHEN series % 20 = 0 THEN NULL ELSE now() END
FROM generate_series(1, 100000) AS series;

INSERT INTO bench_collaboration_items (
    id, scope_type, scope_id, type, tag, title, content, status, pinned,
    created_by_id, created_by_email, created_at, updated_at
)
SELECT md5('collaboration-' || series)::uuid,
       CASE WHEN series % 2 = 0 THEN 'INSTANCE' ELSE 'ORGANIZATION' END,
       md5('scope-' || (((series - 1) % 20000) + 1))::uuid,
       CASE WHEN series % 3 = 0 THEN 'NOTE'
            WHEN series % 3 = 1 THEN 'NOTICE'
            ELSE 'REQUEST' END,
       'tag-' || (series % 20),
       'Benchmark collaboration ' || series,
       'Synthetic benchmark content',
       CASE WHEN series % 3 = 2 THEN 'UNSOLVED' ELSE NULL END,
       series % 20 = 0,
       'usr-' || lpad((((series - 1) % 10000) + 1)::text, 32, '0'),
       'member' || (((series - 1) % 10000) + 1) || '@benchmark.invalid',
       now() - (500000 - series) * interval '1 second',
       now() - (500000 - series) * interval '1 second'
FROM generate_series(1, 500000) AS series;

INSERT INTO bench_collaboration_tags (
    id, scope_type, scope_id, name, usage_count, last_used_at,
    created_by, created_at
)
SELECT md5('tag-' || series)::uuid,
       CASE WHEN (((series - 1) % 20000) + 1) % 2 = 0
            THEN 'INSTANCE' ELSE 'ORGANIZATION' END,
       md5('scope-' || (((series - 1) % 20000) + 1))::uuid,
       'tag-' || ((series - 1) / 20000),
       series % 1000,
       now() - (100000 - series) * interval '1 second',
       'benchmark-user',
       now() - (100000 - series) * interval '1 second'
FROM generate_series(1, 100000) AS series;

ANALYZE bench_vms;
ANALYZE bench_vm_ports;
ANALYZE bench_organizations;
ANALYZE bench_organization_members;
ANALYZE bench_collaboration_items;
ANALYZE bench_collaboration_tags;

\echo '=== SYNTHETIC TABLE SIZES ==='
SELECT 'vms' AS table_name, count(*) AS rows FROM bench_vms
UNION ALL SELECT 'vm_ports', count(*) FROM bench_vm_ports
UNION ALL SELECT 'organizations', count(*) FROM bench_organizations
UNION ALL SELECT 'organization_members', count(*) FROM bench_organization_members
UNION ALL SELECT 'collaboration_items', count(*) FROM bench_collaboration_items
UNION ALL SELECT 'collaboration_tags', count(*) FROM bench_collaboration_tags
ORDER BY table_name;

\echo '=== V1 ACTIVE USER VM LIST ==='
EXPLAIN (ANALYZE, BUFFERS, SUMMARY)
SELECT * FROM bench_vms
WHERE user_id = 'usr-00000000000000000000000000000001' AND deleted_at IS NULL;

\echo '=== V2 ACTIVE PROXMOX VMIDS ==='
EXPLAIN (ANALYZE, BUFFERS, SUMMARY)
SELECT vmid FROM bench_vms WHERE vmid IS NOT NULL AND deleted_at IS NULL;

\echo '=== V3 USER PLAN VM COUNT ==='
EXPLAIN (ANALYZE, BUFFERS, SUMMARY)
SELECT count(*) FROM bench_vms
WHERE user_id = 'usr-00000000000000000000000000000001'
  AND deleted_at IS NULL
  AND status NOT IN ('PENDING', 'FAILED', 'DELETED')
  AND plan_type = 'FREE';

\echo '=== V4 VM PORT LIST ==='
EXPLAIN (ANALYZE, BUFFERS, SUMMARY)
SELECT * FROM bench_vm_ports
WHERE vm_id = '00000000-0000-0000-0000-000000000001'::uuid;

\echo '=== V5 VM PORT COLLISION COUNT ==='
EXPLAIN (ANALYZE, BUFFERS, SUMMARY)
SELECT count(*) FROM bench_vm_ports
WHERE vm_id = '00000000-0000-0000-0000-000000000001'::uuid AND port = 8001;

\echo '=== V6 ORGANIZATIONS BY MEMBER EMAIL ==='
EXPLAIN (ANALYZE, BUFFERS, SUMMARY)
SELECT organization.* FROM bench_organizations organization
JOIN bench_organization_members member ON organization.id = member.organization_id
WHERE member.email = 'member1@benchmark.invalid' AND member.status = 'ACCEPTED';

\echo '=== V7 COLLABORATION SCOPE ORDER ==='
EXPLAIN (ANALYZE, BUFFERS, SUMMARY)
SELECT * FROM bench_collaboration_items
WHERE scope_type = 'ORGANIZATION' AND scope_id = md5('scope-1')::uuid
ORDER BY pinned DESC, created_at DESC;

\echo '=== V8 COLLABORATION SCOPE AND TYPE ORDER ==='
EXPLAIN (ANALYZE, BUFFERS, SUMMARY)
SELECT * FROM bench_collaboration_items
WHERE scope_type = 'ORGANIZATION' AND scope_id = md5('scope-1')::uuid
  AND type = 'NOTICE'
ORDER BY pinned DESC, created_at DESC;

\echo '=== V9 TAG PREFIX SEARCH ==='
EXPLAIN (ANALYZE, BUFFERS, SUMMARY)
SELECT * FROM bench_collaboration_tags
WHERE scope_type = 'ORGANIZATION' AND scope_id = md5('scope-1')::uuid
  AND name LIKE 'ta%'
ORDER BY usage_count DESC, last_used_at DESC LIMIT 8;

\echo '=== V10 ADMIN VM PAGE ==='
EXPLAIN (ANALYZE, BUFFERS, SUMMARY)
SELECT * FROM bench_vms
WHERE deleted_at IS NULL
ORDER BY created_at DESC
LIMIT 50;
