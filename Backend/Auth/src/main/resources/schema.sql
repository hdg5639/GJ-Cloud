CREATE TABLE IF NOT EXISTS users (
    id          VARCHAR(36)  PRIMARY KEY,
    email       VARCHAR(255) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    role        VARCHAR(20)  NOT NULL DEFAULT 'USER',
    status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING_VERIFICATION',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at  DATETIME     NULL
);

-- REL-001: 회원 탈퇴 시 User/VM 서비스 데이터 정리를 재시도 가능하게 추적하는 아웃박스 테이블.
-- 즉시 시도는 AuthServiceImpl.withdraw()에서, 실패분 재시도는 AccountDeletionRetryScheduler가 처리.
CREATE TABLE IF NOT EXISTS account_deletion_jobs (
    id                 VARCHAR(36)  PRIMARY KEY,
    user_id            VARCHAR(36)  NOT NULL,
    email              VARCHAR(255) NOT NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    user_service_done  BOOLEAN      NOT NULL DEFAULT FALSE,
    vm_service_done    BOOLEAN      NOT NULL DEFAULT FALSE,
    attempt_count      INT          NOT NULL DEFAULT 0,
    last_error         VARCHAR(1000) NULL,
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_account_deletion_jobs_status (status)
);
