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

CREATE TABLE IF NOT EXISTS support_inquiries (
    id                    BINARY(16)   NOT NULL,
    user_id               VARCHAR(36)  NOT NULL,
    requester_email       VARCHAR(255) NOT NULL,
    category              VARCHAR(20)  NOT NULL,
    title                 VARCHAR(120) NOT NULL,
    content               MEDIUMTEXT   NOT NULL,
    source_article_slug   VARCHAR(120),
    source_article_title  VARCHAR(180),
    status                VARCHAR(20)  NOT NULL,
    response              MEDIUMTEXT,
    responded_by          VARCHAR(36),
    responded_at          DATETIME(6),
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_support_inquiries_user
        FOREIGN KEY (user_id) REFERENCES user_profiles(user_id) ON DELETE CASCADE,
    INDEX idx_support_inquiries_user_created (user_id, created_at DESC),
    INDEX idx_support_inquiries_status_created (status, created_at DESC)
);

-- MySQL은 CREATE INDEX IF NOT EXISTS를 지원하지 않으므로 information_schema로
-- 확인한 뒤 동적 DDL을 실행한다. schema.sql이 매 기동 실행돼도 안전하다.
SET @index_sql = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'user_profiles'
       AND index_name = 'idx_user_profiles_created_at') = 0,
    'CREATE INDEX idx_user_profiles_created_at ON user_profiles(created_at DESC)',
    'SELECT 1'
);
PREPARE index_stmt FROM @index_sql;
EXECUTE index_stmt;
DEALLOCATE PREPARE index_stmt;

SET @index_sql = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'docs_articles'
       AND index_name = 'idx_docs_articles_fulltext') = 0,
    'CREATE FULLTEXT INDEX idx_docs_articles_fulltext ON docs_articles(title, summary, category) WITH PARSER ngram',
    'SELECT 1'
);
PREPARE index_stmt FROM @index_sql;
EXECUTE index_stmt;
DEALLOCATE PREPARE index_stmt;

SET @index_sql = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'docs_article_tags'
       AND index_name = 'idx_docs_article_tags_fulltext') = 0,
    'CREATE FULLTEXT INDEX idx_docs_article_tags_fulltext ON docs_article_tags(tag) WITH PARSER ngram',
    'SELECT 1'
);
PREPARE index_stmt FROM @index_sql;
EXECUTE index_stmt;
DEALLOCATE PREPARE index_stmt;

SET @index_sql = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'docs_article_tags'
       AND index_name = 'idx_docs_article_tags_tag_article') > 0,
    'DROP INDEX idx_docs_article_tags_tag_article ON docs_article_tags',
    'SELECT 1'
);
PREPARE index_stmt FROM @index_sql;
EXECUTE index_stmt;
DEALLOCATE PREPARE index_stmt;

-- 정렬 방향이 모두 ASC인 기존 인덱스는 복합 정렬에서 filesort를
-- 유발하고 신규 정렬 인덱스와 중복되므로 제거한다.
SET @index_sql = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'docs_articles'
       AND index_name = 'idx_docs_articles_status_sort') > 0,
    'DROP INDEX idx_docs_articles_status_sort ON docs_articles',
    'SELECT 1'
);
PREPARE index_stmt FROM @index_sql;
EXECUTE index_stmt;
DEALLOCATE PREPARE index_stmt;

SET @index_sql = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'user_profiles'
       AND index_name = 'idx_user_profiles_email') = 0,
    'CREATE INDEX idx_user_profiles_email ON user_profiles(email)',
    'SELECT 1'
);
PREPARE index_stmt FROM @index_sql;
EXECUTE index_stmt;
DEALLOCATE PREPARE index_stmt;

SET @index_sql = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'user_profiles'
       AND index_name = 'idx_user_profiles_nickname') = 0,
    'CREATE INDEX idx_user_profiles_nickname ON user_profiles(nickname)',
    'SELECT 1'
);
PREPARE index_stmt FROM @index_sql;
EXECUTE index_stmt;
DEALLOCATE PREPARE index_stmt;

SET @index_sql = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'upgrade_requests'
       AND index_name = 'idx_upgrade_requests_user_status') = 0,
    'CREATE INDEX idx_upgrade_requests_user_status ON upgrade_requests(user_id, status)',
    'SELECT 1'
);
PREPARE index_stmt FROM @index_sql;
EXECUTE index_stmt;
DEALLOCATE PREPARE index_stmt;

SET @index_sql = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'docs_articles'
       AND index_name = 'idx_docs_articles_published_order') = 0,
    'CREATE INDEX idx_docs_articles_published_order ON docs_articles(status, featured DESC, sort_order ASC, published_at DESC)',
    'SELECT 1'
);
PREPARE index_stmt FROM @index_sql;
EXECUTE index_stmt;
DEALLOCATE PREPARE index_stmt;

SET @index_sql = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'docs_articles'
       AND index_name = 'idx_docs_articles_updated_at') = 0,
    'CREATE INDEX idx_docs_articles_updated_at ON docs_articles(updated_at DESC)',
    'SELECT 1'
);
PREPARE index_stmt FROM @index_sql;
EXECUTE index_stmt;
DEALLOCATE PREPARE index_stmt;

SET @index_sql = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'docs_articles'
       AND index_name = 'idx_docs_articles_status_category_order') = 0,
    'CREATE INDEX idx_docs_articles_status_category_order ON docs_articles(status, category, featured DESC, sort_order ASC, published_at DESC)',
    'SELECT 1'
);
PREPARE index_stmt FROM @index_sql;
EXECUTE index_stmt;
DEALLOCATE PREPARE index_stmt;

SET @index_sql = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'support_inquiries'
       AND index_name = 'idx_support_inquiries_created_at') = 0,
    'CREATE INDEX idx_support_inquiries_created_at ON support_inquiries(created_at DESC)',
    'SELECT 1'
);
PREPARE index_stmt FROM @index_sql;
EXECUTE index_stmt;
DEALLOCATE PREPARE index_stmt;
