CREATE TABLE IF NOT EXISTS user_profiles (
    user_id           VARCHAR(36)  NOT NULL,
    email             VARCHAR(255) NOT NULL,
    nickname          VARCHAR(50),
    profile_image_url VARCHAR(500),
    plan_type         VARCHAR(20)  NOT NULL DEFAULT 'FREE',
    suspended         TINYINT(1)   NOT NULL DEFAULT 0,
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

CREATE TABLE IF NOT EXISTS upgrade_requests (
    id                  BINARY(16)   NOT NULL,
    user_id             VARCHAR(255) NOT NULL,
    type                VARCHAR(50)  NOT NULL,
    target_plan_type    VARCHAR(50)  NOT NULL,
    status              VARCHAR(50)  NOT NULL,
    reason              VARCHAR(255),
    created_at          DATETIME(6)  NOT NULL,
    reviewed_at         DATETIME(6),
    reviewed_by         VARCHAR(255),
    PRIMARY KEY (id),
    FOREIGN KEY (user_id) REFERENCES user_profiles(user_id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_status_created (status, created_at DESC)
);

CREATE TABLE IF NOT EXISTS docs_articles (
    id               BINARY(16)   NOT NULL,
    slug             VARCHAR(120) NOT NULL,
    title            VARCHAR(180) NOT NULL,
    summary          VARCHAR(400) NOT NULL,
    category         VARCHAR(80)  NOT NULL,
    cover_image_url  VARCHAR(500),
    content          MEDIUMTEXT   NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    featured         TINYINT(1)   NOT NULL DEFAULT 0,
    sort_order       INT          NOT NULL DEFAULT 0,
    view_count       BIGINT       NOT NULL DEFAULT 0,
    author_id        VARCHAR(36)  NOT NULL,
    published_at     DATETIME(6),
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_docs_articles_slug (slug),
    INDEX idx_docs_articles_status_sort (status, featured, sort_order, published_at),
    INDEX idx_docs_articles_category (category)
);

CREATE TABLE IF NOT EXISTS docs_article_tags (
    article_id BINARY(16)  NOT NULL,
    tag         VARCHAR(60) NOT NULL,
    tag_order   INT         NOT NULL,
    PRIMARY KEY (article_id, tag_order),
    CONSTRAINT fk_docs_article_tags_article
        FOREIGN KEY (article_id) REFERENCES docs_articles(id) ON DELETE CASCADE
);
