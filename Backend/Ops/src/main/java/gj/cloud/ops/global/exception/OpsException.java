package gj.cloud.ops.global.exception;

import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import lombok.Getter;

@Getter
public class OpsException extends RuntimeException {
    private final OpsErrorCode errorCode;

    public OpsException(OpsErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public OpsException(OpsErrorCode errorCode, String message) {
        super(message == null || message.isBlank() ? errorCode.getMessage() : message);
        this.errorCode = errorCode;
    }
}
