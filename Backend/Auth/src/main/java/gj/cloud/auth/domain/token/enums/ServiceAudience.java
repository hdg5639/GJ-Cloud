package gj.cloud.auth.domain.token.enums;

import gj.cloud.auth.global.exception.AuthException;
import gj.cloud.auth.global.exception.enums.AuthErrorCode;

import java.util.Arrays;

public enum ServiceAudience {
    AUTH("auth-service"),
    USER("user-service"),
    VM("vm-service"),
    OPS("ops-service");

    private final String value;

    ServiceAudience(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ServiceAudience from(String value) {
        return Arrays.stream(values())
                .filter(a -> a.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new AuthException(AuthErrorCode.INVALID_AUDIENCE));
    }
}
