package gj.cloud.vm.global.exception;

import gj.cloud.vm.global.exception.enums.VmErrorCode;
import lombok.Getter;

@Getter
public class VmException extends RuntimeException {

    private final VmErrorCode errorCode;

    public VmException(VmErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
