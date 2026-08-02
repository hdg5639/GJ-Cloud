CREATE TABLE IF NOT EXISTS vms (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         VARCHAR(36)  NOT NULL,
    vmid            INTEGER,
    name            VARCHAR(100) NOT NULL,
    plan_type       VARCHAR(20)  NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    ssh_key_id      VARCHAR(36)  NOT NULL,
    internal_ip     VARCHAR(45),
    proxmox_task_id VARCHAR(255),
    error_message   TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMP,

    CONSTRAINT chk_plan_type CHECK (plan_type IN ('FREE', 'PRO')),
    CONSTRAINT chk_status    CHECK (status IN ('PENDING', 'CREATING', 'BOOTING', 'RUNNING', 'STARTING', 'STOPPING', 'STOPPED', 'SUSPENDING', 'SUSPENDED', 'FAILED', 'DELETING', 'DELETED'))
);

CREATE INDEX IF NOT EXISTS idx_vms_user_id ON vms(user_id);
CREATE INDEX IF NOT EXISTS idx_vms_status  ON vms(status);

ALTER TABLE vms DROP CONSTRAINT IF EXISTS chk_status;
ALTER TABLE vms ADD CONSTRAINT chk_status CHECK (status IN ('PENDING', 'CREATING', 'BOOTING', 'RUNNING', 'STARTING', 'STOPPING', 'STOPPED', 'SUSPENDING', 'SUSPENDED', 'FAILED', 'DELETING', 'DELETED'));

ALTER TABLE vms ADD COLUMN IF NOT EXISTS subdomain        VARCHAR(20) UNIQUE;
ALTER TABLE vms ADD COLUMN IF NOT EXISTS cf_dns_record_id VARCHAR(255);
ALTER TABLE vms ADD COLUMN IF NOT EXISTS cf_app_id        VARCHAR(255);
ALTER TABLE vms ADD COLUMN IF NOT EXISTS cf_policy_id     VARCHAR(255);
ALTER TABLE vms ADD COLUMN IF NOT EXISTS disk_size_gb     INTEGER NOT NULL DEFAULT 20;

CREATE TABLE IF NOT EXISTS vm_ports (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vm_id            UUID NOT NULL REFERENCES vms(id),
    port             INTEGER NOT NULL,
    protocol         VARCHAR(10) NOT NULL,
    visibility       VARCHAR(10) NOT NULL,
    nickname         VARCHAR(20) NOT NULL,
    subdomain        VARCHAR(40) UNIQUE NOT NULL,
    cf_dns_record_id VARCHAR(255),
    cf_app_id        VARCHAR(255),
    cf_policy_id     VARCHAR(255),
    created_at       TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT chk_port_protocol   CHECK (protocol   IN ('HTTP', 'TCP')),
    CONSTRAINT chk_port_visibility CHECK (visibility IN ('PUBLIC', 'PRIVATE')),
    CONSTRAINT uq_vm_port_nickname UNIQUE (vm_id, nickname)
);

-- 단일 진입 포트 호스트 라우팅: 같은 배포의 여러 도메인 CNAME이 하나의 Caddy router 포트를 공유하므로
-- (vm_id, port) 유일성은 더 이상 성립하지 않는다. 대신 subdomain(UNIQUE)·nickname(UNIQUE per vm)으로
-- 각 라우트를 구분하고, 포트 충돌은 애플리케이션 레벨에서 소유자 기준으로 검사한다.
ALTER TABLE vm_ports DROP CONSTRAINT IF EXISTS uq_vm_port;

ALTER TABLE vm_ports ADD COLUMN IF NOT EXISTS nickname VARCHAR(20);
CREATE UNIQUE INDEX IF NOT EXISTS uq_vm_port_nickname ON vm_ports(vm_id, nickname);

-- 배포(Ops)가 생성한 포트와 사용자가 수동으로 추가한 포트를 구분하기 위한 태그.
-- NULL이면 수동 추가 — 배포 라우트 동기화(sync)는 이 값이 있는 행만 add/remove 대상으로 삼음.
ALTER TABLE vm_ports ADD COLUMN IF NOT EXISTS deployment_id VARCHAR(36);
CREATE INDEX IF NOT EXISTS idx_vm_ports_deployment_id ON vm_ports(vm_id, deployment_id);
ALTER TABLE vm_ports ADD COLUMN IF NOT EXISTS deployment_app_id VARCHAR(36);
-- 기존 단일 앱 배포 포트는 vmId를 appId로 사용했으므로 무중단 마이그레이션 시 동일한 값으로 보강한다.
UPDATE vm_ports SET deployment_app_id = vm_id::text
WHERE deployment_id IS NOT NULL AND deployment_app_id IS NULL;
CREATE INDEX IF NOT EXISTS idx_vm_ports_deployment_app_id ON vm_ports(vm_id, deployment_app_id);
-- 사용자가 직접 만든 포트를 배포 대상 카드에 표시하기 위한 느슨한 연결.
-- deployment_app_id는 자동 배포 라우트의 생명주기 소유권이므로 재사용하지 않는다.
ALTER TABLE vm_ports ADD COLUMN IF NOT EXISTS linked_deployment_target_id VARCHAR(36);
CREATE INDEX IF NOT EXISTS idx_vm_ports_linked_deployment_target_id
    ON vm_ports(vm_id, linked_deployment_target_id);

CREATE TABLE IF NOT EXISTS vm_port_access_emails (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vm_port_id UUID NOT NULL REFERENCES vm_ports(id),
    email      VARCHAR(255) NOT NULL,

    CONSTRAINT uq_port_email UNIQUE (vm_port_id, email)
);

CREATE TABLE IF NOT EXISTS vm_ssh_access_emails (
    id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vm_id  UUID NOT NULL REFERENCES vms(id),
    email  VARCHAR(255) NOT NULL,

    CONSTRAINT uq_vm_ssh_email UNIQUE (vm_id, email)
);

-- ── Organization ──────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS organizations (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(100) NOT NULL,
    owner_id   VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_organizations_owner_id ON organizations(owner_id);

CREATE TABLE IF NOT EXISTS organization_members (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    email           VARCHAR(255) NOT NULL,
    user_id         VARCHAR(255),
    role            VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    invited_at      TIMESTAMP NOT NULL DEFAULT now(),
    joined_at       TIMESTAMP,

    CONSTRAINT chk_member_role   CHECK (role   IN ('OWNER', 'ADMIN', 'MEMBER')),
    CONSTRAINT chk_member_status CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED')),
    CONSTRAINT uq_org_member_email UNIQUE (organization_id, email)
);

CREATE INDEX IF NOT EXISTS idx_org_members_org_id ON organization_members(organization_id);
CREATE INDEX IF NOT EXISTS idx_org_members_email  ON organization_members(email);

-- 조직 초대 검색 UX(닉네임/이미지 표시) — email처럼 초대 시점 스냅샷을 비정규화해서 저장
ALTER TABLE organization_members ADD COLUMN IF NOT EXISTS nickname          VARCHAR(50);
ALTER TABLE organization_members ADD COLUMN IF NOT EXISTS profile_image_url VARCHAR(500);

CREATE TABLE IF NOT EXISTS organization_vms (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    vm_id           UUID NOT NULL,
    added_at        TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT uq_org_vm UNIQUE (organization_id, vm_id)
);

CREATE INDEX IF NOT EXISTS idx_org_vms_org_id ON organization_vms(organization_id);
CREATE INDEX IF NOT EXISTS idx_org_vms_vm_id  ON organization_vms(vm_id);

-- ── Collaboration ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS collaboration_items (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scope_type        VARCHAR(20) NOT NULL,
    scope_id          UUID NOT NULL,
    type              VARCHAR(20) NOT NULL,
    tag               VARCHAR(50),
    title             VARCHAR(200) NOT NULL,
    content           TEXT NOT NULL,
    status            VARCHAR(20),
    pinned            BOOLEAN NOT NULL DEFAULT false,
    created_by_id     VARCHAR(255) NOT NULL,
    created_by_email  VARCHAR(255) NOT NULL,
    resolved_by_id    VARCHAR(255),
    resolved_by_email VARCHAR(255),
    created_at        TIMESTAMP NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT chk_collab_scope_type CHECK (scope_type IN ('ORGANIZATION', 'INSTANCE')),
    CONSTRAINT chk_collab_type       CHECK (type IN ('NOTE', 'NOTICE', 'REQUEST')),
    CONSTRAINT chk_collab_status     CHECK (status IS NULL OR status IN ('UNSOLVED', 'SOLVED'))
);

CREATE INDEX IF NOT EXISTS idx_collab_scope ON collaboration_items(scope_type, scope_id);

CREATE TABLE IF NOT EXISTS collaboration_tags (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scope_type   VARCHAR(20) NOT NULL,
    scope_id     UUID NOT NULL,
    name         VARCHAR(50) NOT NULL,
    usage_count  INTEGER NOT NULL DEFAULT 1,
    last_used_at TIMESTAMP NOT NULL DEFAULT now(),
    created_by   VARCHAR(255) NOT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT chk_tag_scope_type CHECK (scope_type IN ('ORGANIZATION', 'INSTANCE')),
    CONSTRAINT uq_tag_scope_name  UNIQUE (scope_type, scope_id, name)
);

CREATE INDEX IF NOT EXISTS idx_collab_tags_scope ON collaboration_tags(scope_type, scope_id);
