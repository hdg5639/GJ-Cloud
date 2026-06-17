CREATE TABLE IF NOT EXISTS user_profiles (
    user_id           VARCHAR(36)  NOT NULL,
    email             VARCHAR(255) NOT NULL,
    nickname          VARCHAR(50),
    profile_image_url VARCHAR(500),
    plan_type         VARCHAR(20)  NOT NULL DEFAULT 'FREE',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id)
);

CREATE TABLE IF NOT EXISTS ssh_keys (
    id          VARCHAR(36)  NOT NULL,
    user_id     VARCHAR(36)  NOT NULL,
    public_key  TEXT         NOT NULL,
    fingerprint VARCHAR(255) NOT NULL,
    name        VARCHAR(100) NOT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_fingerprint (fingerprint),
    INDEX idx_user_id (user_id)
);
