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
    CONSTRAINT uq_vm_port          UNIQUE (vm_id, port),
    CONSTRAINT uq_vm_port_nickname UNIQUE (vm_id, nickname)
);

ALTER TABLE vm_ports ADD COLUMN IF NOT EXISTS nickname VARCHAR(20);
DO $$ BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'uq_vm_port_nickname'
  ) THEN
    ALTER TABLE vm_ports ADD CONSTRAINT uq_vm_port_nickname UNIQUE (vm_id, nickname);
  END IF;
END $$;

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
