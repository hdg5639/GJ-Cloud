package gj.cloud.user.global.exception;

import gj.cloud.user.global.exception.enums.UserErrorCode;
import lombok.Getter;

@Getter
public class UserException extends RuntimeException {
    private final UserErrorCode errorCode;

    public UserException(UserErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
