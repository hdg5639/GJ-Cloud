package gj.cloud.auth.global.exception;

import gj.cloud.auth.global.exception.enums.AuthErrorCode;
import lombok.Getter;

@Getter
public class AuthException extends RuntimeException {
    private final AuthErrorCode errorCode;
    private final Long retryAfterSeconds;

    public AuthException(AuthErrorCode errorCode) {
        this(errorCode, null);
    }

    public AuthException(AuthErrorCode errorCode, Long retryAfterSeconds) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
