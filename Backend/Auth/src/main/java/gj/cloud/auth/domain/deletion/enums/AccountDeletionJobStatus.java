package gj.cloud.auth.domain.deletion.enums;

public enum AccountDeletionJobStatus {
    PENDING,
    COMPLETED,
    FAILED_RETRYABLE,
    FAILED_MANUAL_REVIEW
}
