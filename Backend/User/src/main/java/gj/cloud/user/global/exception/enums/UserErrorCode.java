package gj.cloud.user.global.exception.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode {
    PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "프로필을 찾을 수 없습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    SSH_KEY_NOT_FOUND(HttpStatus.NOT_FOUND, "SSH 키를 찾을 수 없습니다."),
    REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "요청을 찾을 수 없습니다."),
    SSH_KEY_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 등록된 SSH 키입니다."),
    PENDING_REQUEST_EXISTS(HttpStatus.CONFLICT, "이미 진행 중인 요청이 있습니다."),
    SSH_KEY_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "SSH 키 등록 개수를 초과했습니다."),
    INVALID_SSH_KEY_FORMAT(HttpStatus.BAD_REQUEST, "유효하지 않은 SSH 키 형식입니다."),
    INVALID_PLAN_TYPE(HttpStatus.BAD_REQUEST, "같은 플랜으로는 변경할 수 없습니다."),
    INVALID_REQUEST_STATUS(HttpStatus.BAD_REQUEST, "처리할 수 없는 요청 상태입니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    ADMIN_ACCESS_REQUIRED(HttpStatus.FORBIDDEN, "관리자 권한이 필요합니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    INVALID_AUDIENCE(HttpStatus.UNAUTHORIZED, "이 서비스에 유효하지 않은 토큰입니다."),
    KEY_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "SSH 키 생성에 실패했습니다.");

    private final HttpStatus status;
    private final String message;
}
