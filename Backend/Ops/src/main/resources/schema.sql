CREATE TABLE IF NOT EXISTS vm_management_keys (
    id                       VARCHAR(36) PRIMARY KEY,
    vm_id                    VARCHAR(36) NOT NULL UNIQUE,
    public_key               TEXT        NOT NULL,
    encrypted_private_key    TEXT        NOT NULL,
    status                   VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ssh_host_key_fingerprint VARCHAR(200),
    created_at               TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at               TIMESTAMP   NOT NULL DEFAULT now(),

    CONSTRAINT chk_key_status CHECK (status IN ('ACTIVE', 'REVOKE_PENDING', 'REVOKED', 'ORPHANED'))
);

-- SEC-011: 기존에 배포된 테이블에는 CREATE TABLE IF NOT EXISTS가 컬럼을 추가해주지 않으므로 별도 처리
ALTER TABLE vm_management_keys ADD COLUMN IF NOT EXISTS ssh_host_key_fingerprint VARCHAR(200);

CREATE INDEX IF NOT EXISTS idx_vm_management_keys_status ON vm_management_keys(status);

CREATE TABLE IF NOT EXISTS github_installations (
    id              VARCHAR(100) PRIMARY KEY,
    installation_id BIGINT       NOT NULL,
    user_id         VARCHAR(255) NOT NULL,
    account_login   VARCHAR(255) NOT NULL,
    account_type    VARCHAR(50)  NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT now()
);

-- GitHub 조직 App 설치 하나를 여러 GamjaBox 사용자가 각자 연결할 수 있다. 초기 구현의
-- installation_id 단일 PK 테이블이 이미 존재해도 사용자별 매핑 테이블로 무중단 전환한다.
ALTER TABLE github_installations ADD COLUMN IF NOT EXISTS id VARCHAR(100);
UPDATE github_installations
SET id = installation_id::text || ':' || user_id
WHERE id IS NULL;
ALTER TABLE github_installations ALTER COLUMN id SET NOT NULL;
ALTER TABLE github_installations DROP CONSTRAINT IF EXISTS github_installations_pkey;
ALTER TABLE github_installations ADD CONSTRAINT github_installations_pkey PRIMARY KEY (id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_github_installation_user
    ON github_installations(installation_id, user_id);
CREATE INDEX IF NOT EXISTS idx_github_installations_user_id ON github_installations(user_id);

CREATE TABLE IF NOT EXISTS deployment_targets (
    id                           VARCHAR(36)  PRIMARY KEY,
    vm_id                        VARCHAR(36)  NOT NULL,
    owner_user_id                VARCHAR(255) NOT NULL,
    owner_email                  VARCHAR(255) NOT NULL,
    name                         VARCHAR(60)  NOT NULL,
    repository_url               VARCHAR(500) NOT NULL,
    branch                       VARCHAR(255) NOT NULL,
    github_installation_id       BIGINT,
    github_repository_id         BIGINT,
    github_repository_full_name  VARCHAR(255),
    source_type                  VARCHAR(20)  NOT NULL,
    source_compose_ciphertext    TEXT         NOT NULL,
    environment_files_ciphertext TEXT,
    uploaded_files_ciphertext    TEXT,
    exposed_routes_json          TEXT,
    health_checks_json           TEXT,
    context                      VARCHAR(255),
    install_path                 VARCHAR(500),
    auto_deploy_enabled          BOOLEAN      NOT NULL DEFAULT false,
    active                       BOOLEAN      NOT NULL DEFAULT true,
    latest_requested_revision    VARCHAR(64),
    latest_requested_at          TIMESTAMP,
    latest_deployed_revision     VARCHAR(64),
    latest_deployment_id         VARCHAR(36),
    created_at                   TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at                   TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT chk_deployment_target_source_type CHECK (source_type IN ('TEMPLATE_SPEC', 'AI_SPEC', 'RAW_COMPOSE'))
);

CREATE INDEX IF NOT EXISTS idx_deployment_targets_vm_id ON deployment_targets(vm_id);
ALTER TABLE deployment_targets DROP CONSTRAINT IF EXISTS uq_deployment_target_vm_name;
CREATE UNIQUE INDEX IF NOT EXISTS uq_deployment_target_vm_name_ci
    ON deployment_targets(vm_id, lower(name)) WHERE active;
CREATE INDEX IF NOT EXISTS idx_deployment_targets_github_repo
    ON deployment_targets(github_installation_id, github_repository_id);
ALTER TABLE deployment_targets ADD COLUMN IF NOT EXISTS latest_requested_at TIMESTAMP;

-- Auto Preview(SourceType.AUTO_PREVIEW) 반영 — 기존 배포 환경의 CHECK 제약을 확장
ALTER TABLE deployment_targets DROP CONSTRAINT IF EXISTS chk_deployment_target_source_type;
ALTER TABLE deployment_targets ADD CONSTRAINT chk_deployment_target_source_type
    CHECK (source_type IN ('TEMPLATE_SPEC', 'AI_SPEC', 'RAW_COMPOSE', 'AUTO_PREVIEW'));

CREATE TABLE IF NOT EXISTS deployments (
    id                          VARCHAR(36)  PRIMARY KEY,
    vm_id                       VARCHAR(36)  NOT NULL,
    deployment_target_id        VARCHAR(36),
    trigger_type                VARCHAR(20)  NOT NULL DEFAULT 'MANUAL',
    requested_revision          VARCHAR(64),
    status                      VARCHAR(20)  NOT NULL DEFAULT 'QUEUED',
    source_type                 VARCHAR(20)  NOT NULL,
    source_revision             VARCHAR(64),
    source_compose_ciphertext   TEXT,
    resolved_compose_ciphertext TEXT,
    service_image_refs_json     TEXT,
    environment_files_ciphertext TEXT,
    exposed_routes_json         TEXT,
    health_checks_json          TEXT,
    preview_blueprint_json      TEXT,
    release_dir                 VARCHAR(500),
    context                     VARCHAR(255),
    install_path                VARCHAR(500),
    env_version                 INTEGER,
    previous_deployment_id      VARCHAR(36),
    error_message               TEXT,
    created_at                  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMP    NOT NULL DEFAULT now(),
    deployed_at                 TIMESTAMP,

    CONSTRAINT chk_deployment_status CHECK (status IN (
        'QUEUED', 'CLONING', 'UPLOADING', 'VALIDATING', 'BUILDING', 'SWAPPING',
        'HEALTH_CHECKING', 'ROUTING', 'SUCCEEDED', 'FAILED', 'ROLLING_BACK', 'ROLLED_BACK',
        'STOPPING', 'STOPPED'
    )),
    CONSTRAINT chk_source_type CHECK (source_type IN ('TEMPLATE_SPEC', 'AI_SPEC', 'RAW_COMPOSE'))
);

ALTER TABLE deployments DROP CONSTRAINT IF EXISTS chk_source_type;
ALTER TABLE deployments ADD CONSTRAINT chk_source_type
    CHECK (source_type IN ('TEMPLATE_SPEC', 'AI_SPEC', 'RAW_COMPOSE', 'AUTO_PREVIEW'));

-- 재시도/수정 후 재배포(compose-spec 조회 API)를 위해 추가 — 기존 배포 환경에서도 반영되도록 idempotent하게 추가
ALTER TABLE deployments ADD COLUMN IF NOT EXISTS environment_files_ciphertext TEXT;
ALTER TABLE deployments ADD COLUMN IF NOT EXISTS exposed_routes_json TEXT;
ALTER TABLE deployments ADD COLUMN IF NOT EXISTS health_checks_json TEXT;
-- Raw Compose 배포 시 저장소 내 특정 서브디렉토리를 배포 컨텍스트로 선택하는 기능 추가
ALTER TABLE deployments ADD COLUMN IF NOT EXISTS context VARCHAR(255);
-- VM 내 특정 경로에 현재 배포를 가리키는 심볼릭 링크를 생성하는 기능 추가
ALTER TABLE deployments ADD COLUMN IF NOT EXISTS install_path VARCHAR(500);
ALTER TABLE deployments ADD COLUMN IF NOT EXISTS deployment_target_id VARCHAR(36);
ALTER TABLE deployments ADD COLUMN IF NOT EXISTS trigger_type VARCHAR(20) NOT NULL DEFAULT 'MANUAL';
ALTER TABLE deployments ADD COLUMN IF NOT EXISTS requested_revision VARCHAR(64);
-- Auto Preview 배포 시점의 capabilities/pages/authStrategy/Block 배치를 그대로 저장하는 읽기 전용
-- 스냅샷(auto-preview-design/01-blueprint-schema.md §14 MVP 축소판). Raw Compose/Git 배포는 항상 null.
ALTER TABLE deployments ADD COLUMN IF NOT EXISTS preview_blueprint_json TEXT;

-- 배포 내리기 상태(STOPPING/STOPPED)는 기존 운영 테이블이 만들어진 뒤 추가됐다. CREATE TABLE IF NOT
-- EXISTS는 기존 CHECK 제약을 갱신하지 않으므로, 애플리케이션 시작 시 현재 enum 목록으로 재생성한다.
-- Ops는 단일 인스턴스로 운영되고 schema.sql은 트래픽 수신 전에 실행되므로 DDL 경합은 발생하지 않는다.
ALTER TABLE deployments DROP CONSTRAINT IF EXISTS chk_deployment_status;
ALTER TABLE deployments ADD CONSTRAINT chk_deployment_status CHECK (status IN (
    'QUEUED', 'CLONING', 'UPLOADING', 'VALIDATING', 'BUILDING', 'SWAPPING',
    'HEALTH_CHECKING', 'ROUTING', 'SUCCEEDED', 'FAILED', 'ROLLING_BACK', 'ROLLED_BACK',
    'STOPPING', 'STOPPED'
));

CREATE INDEX IF NOT EXISTS idx_deployments_vm_id ON deployments(vm_id);
CREATE INDEX IF NOT EXISTS idx_deployments_vm_id_created_at ON deployments(vm_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_deployments_target_created_at
    ON deployments(deployment_target_id, created_at DESC);

CREATE TABLE IF NOT EXISTS github_webhook_deliveries (
    id         VARCHAR(100) PRIMARY KEY,
    event_type VARCHAR(50)  NOT NULL,
    status     VARCHAR(30)  NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS deployment_events (
    id              VARCHAR(36) PRIMARY KEY,
    deployment_id   VARCHAR(36) NOT NULL,
    sequence        BIGINT      NOT NULL,
    event_type      VARCHAR(20) NOT NULL,
    message         TEXT,
    payload         TEXT,
    created_at      TIMESTAMP   NOT NULL DEFAULT now(),

    CONSTRAINT uq_deployment_events_deployment_sequence UNIQUE (deployment_id, sequence),
    CONSTRAINT chk_deployment_event_type CHECK (event_type IN ('STAGE_CHANGE', 'BUILD_LOG', 'ERROR', 'DONE'))
);

CREATE INDEX IF NOT EXISTS idx_deployment_events_deployment_id_sequence ON deployment_events(deployment_id, sequence);

CREATE TABLE IF NOT EXISTS ai_spec_generation_log (
    id                          VARCHAR(36)  PRIMARY KEY,
    vm_id                       VARCHAR(36)  NOT NULL,
    kind                        VARCHAR(20)  NOT NULL DEFAULT 'GENERATION',
    model                       VARCHAR(100) NOT NULL,
    input_tokens                BIGINT       NOT NULL,
    output_tokens               BIGINT       NOT NULL,
    correction_attempt_count    INTEGER      NOT NULL DEFAULT 0,
    succeeded                   BOOLEAN      NOT NULL,
    used_deterministic_rules    BOOLEAN      NOT NULL DEFAULT false,
    ambiguity_score             INTEGER,
    cache_hit                   BOOLEAN      NOT NULL DEFAULT false,
    created_at                  TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT chk_ai_spec_generation_log_kind CHECK (kind IN ('GENERATION', 'REVIEW'))
);

-- 이미 배포된 환경에서 테이블이 kind 컬럼 없이 먼저 생성됐을 수 있어 ALTER로 보강 (idempotent)
ALTER TABLE ai_spec_generation_log ADD COLUMN IF NOT EXISTS kind VARCHAR(20) NOT NULL DEFAULT 'GENERATION';
-- 결정론적 규칙 기반 추론/ambiguity 라우팅/애플리케이션 캐시 도입(AI-Deployment-Pipeline.md 9·15·16절)에 따른 보강
ALTER TABLE ai_spec_generation_log ADD COLUMN IF NOT EXISTS used_deterministic_rules BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE ai_spec_generation_log ADD COLUMN IF NOT EXISTS ambiguity_score INTEGER;
ALTER TABLE ai_spec_generation_log ADD COLUMN IF NOT EXISTS cache_hit BOOLEAN NOT NULL DEFAULT false;

CREATE INDEX IF NOT EXISTS idx_ai_spec_generation_log_vm_id ON ai_spec_generation_log(vm_id);

-- Auto Preview(GamjaBox_2.0_Key_Features.md 1단계) AI 검수 감사 로그. 분석/검수가 특정 VM에
-- 종속되지 않으므로 ai_spec_generation_log(vm_id 필수)를 그대로 쓰지 않고 requester_user_id로 감사한다.
CREATE TABLE IF NOT EXISTS ai_preview_generation_log (
    id                  VARCHAR(36)  PRIMARY KEY,
    requester_user_id   VARCHAR(64)  NOT NULL,
    kind                VARCHAR(20)  NOT NULL DEFAULT 'REVIEW',
    model               VARCHAR(100) NOT NULL,
    input_tokens        BIGINT       NOT NULL,
    output_tokens       BIGINT       NOT NULL,
    succeeded           BOOLEAN      NOT NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT chk_ai_preview_generation_log_kind CHECK (kind IN ('GENERATION', 'REVIEW'))
);

CREATE INDEX IF NOT EXISTS idx_ai_preview_generation_log_requester ON ai_preview_generation_log(requester_user_id);

CREATE TABLE IF NOT EXISTS db_backups (
    id                  VARCHAR(36)  PRIMARY KEY,
    vm_id               VARCHAR(36)  NOT NULL,
    service_name        VARCHAR(100) NOT NULL,
    db_type             VARCHAR(20)  NOT NULL,
    file_path           TEXT,
    file_size_bytes     BIGINT,
    succeeded           BOOLEAN      NOT NULL,
    error_message       TEXT,
    created_at          TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT chk_db_backups_db_type CHECK (db_type IN ('postgresql', 'mysql', 'redis', 'mongodb'))
);

CREATE INDEX IF NOT EXISTS idx_db_backups_vm_id ON db_backups(vm_id);
CREATE INDEX IF NOT EXISTS idx_db_backups_vm_id_created_at ON db_backups(vm_id, created_at DESC);
