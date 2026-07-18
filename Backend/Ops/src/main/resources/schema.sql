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
    release_dir                 VARCHAR(500),
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

CREATE INDEX IF NOT EXISTS idx_deployments_vm_id ON deployments(vm_id);
CREATE INDEX IF NOT EXISTS idx_deployments_vm_id_created_at ON deployments(vm_id, created_at DESC);
