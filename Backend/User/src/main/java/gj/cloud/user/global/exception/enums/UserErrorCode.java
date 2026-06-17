package gj.cloud.user.global.exception.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode {
    PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "프로필을 찾을 수 없습니다."),
    SSH_KEY_NOT_FOUND(HttpStatus.NOT_FOUND, "SSH 키를 찾을 수 없습니다."),
    SSH_KEY_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 등록된 SSH 키입니다."),
    SSH_KEY_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "SSH 키 등록 개수를 초과했습니다."),
    INVALID_SSH_KEY_FORMAT(HttpStatus.BAD_REQUEST, "유효하지 않은 SSH 키 형식입니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    INVALID_AUDIENCE(HttpStatus.UNAUTHORIZED, "이 서비스에 유효하지 않은 토큰입니다.");

    private final HttpStatus status;
    private final String message;
}
