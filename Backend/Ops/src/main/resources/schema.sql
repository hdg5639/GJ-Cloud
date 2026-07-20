CREATE TABLE IF NOT EXISTS vm_management_keys (
    id                      VARCHAR(36) PRIMARY KEY,
    vm_id                   VARCHAR(36) NOT NULL UNIQUE,
    public_key              TEXT        NOT NULL,
    encrypted_private_key   TEXT        NOT NULL,
    status                  VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at              TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP   NOT NULL DEFAULT now(),

    CONSTRAINT chk_key_status CHECK (status IN ('ACTIVE', 'REVOKE_PENDING', 'REVOKED', 'ORPHANED'))
);

CREATE INDEX IF NOT EXISTS idx_vm_management_keys_status ON vm_management_keys(status);

CREATE TABLE IF NOT EXISTS deployments (
    id                          VARCHAR(36)  PRIMARY KEY,
    vm_id                       VARCHAR(36)  NOT NULL,
    status                      VARCHAR(20)  NOT NULL DEFAULT 'QUEUED',
    source_type                 VARCHAR(20)  NOT NULL,
    source_revision             VARCHAR(64),
    source_compose_ciphertext   TEXT,
    resolved_compose_ciphertext TEXT,
    service_image_refs_json     TEXT,
    environment_files_ciphertext TEXT,
    exposed_routes_json         TEXT,
    health_checks_json          TEXT,
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
        'HEALTH_CHECKING', 'ROUTING', 'SUCCEEDED', 'FAILED', 'ROLLING_BACK', 'ROLLED_BACK'
    )),
    CONSTRAINT chk_source_type CHECK (source_type IN ('TEMPLATE_SPEC', 'AI_SPEC', 'RAW_COMPOSE'))
);

-- 재시도/수정 후 재배포(compose-spec 조회 API)를 위해 추가 — 기존 배포 환경에서도 반영되도록 idempotent하게 추가
ALTER TABLE deployments ADD COLUMN IF NOT EXISTS environment_files_ciphertext TEXT;
ALTER TABLE deployments ADD COLUMN IF NOT EXISTS exposed_routes_json TEXT;
ALTER TABLE deployments ADD COLUMN IF NOT EXISTS health_checks_json TEXT;
-- Raw Compose 배포 시 저장소 내 특정 서브디렉토리를 배포 컨텍스트로 선택하는 기능 추가
ALTER TABLE deployments ADD COLUMN IF NOT EXISTS context VARCHAR(255);
-- VM 내 특정 경로에 현재 배포를 가리키는 심볼릭 링크를 생성하는 기능 추가
ALTER TABLE deployments ADD COLUMN IF NOT EXISTS install_path VARCHAR(500);

CREATE INDEX IF NOT EXISTS idx_deployments_vm_id ON deployments(vm_id);
CREATE INDEX IF NOT EXISTS idx_deployments_vm_id_created_at ON deployments(vm_id, created_at DESC);

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
