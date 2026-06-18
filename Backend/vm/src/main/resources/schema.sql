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
