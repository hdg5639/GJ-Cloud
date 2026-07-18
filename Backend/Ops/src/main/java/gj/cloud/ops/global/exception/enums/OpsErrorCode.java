package gj.cloud.ops.global.exception.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum OpsErrorCode {
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    INVALID_AUDIENCE(HttpStatus.UNAUTHORIZED, "이 서비스에 유효하지 않은 토큰입니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    VM_CONTEXT_FETCH_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "VM 정보 조회에 실패했습니다."),
    VM_NOT_FOUND(HttpStatus.NOT_FOUND, "VM을 찾을 수 없습니다."),
    VM_NOT_RUNNING(HttpStatus.BAD_REQUEST, "VM이 실행 중이 아니므로 접속할 수 없습니다."),
    MANAGEMENT_KEY_NOT_FOUND(HttpStatus.NOT_FOUND, "VM 관리 키를 찾을 수 없습니다."),
    MANAGEMENT_KEY_ISSUE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "관리 키 발급에 실패했습니다."),
    INVALID_TICKET(HttpStatus.UNAUTHORIZED, "유효하지 않거나 만료된 티켓입니다."),
    SSH_CONNECTION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "VM SSH 연결에 실패했습니다."),

    // 파일 브라우저
    INVALID_PATH(HttpStatus.BAD_REQUEST, "유효하지 않은 경로입니다."),
    PATH_ACCESS_DENIED(HttpStatus.FORBIDDEN, "허용되지 않은 경로입니다."),
    INVALID_FILE_NAME(HttpStatus.BAD_REQUEST, "유효하지 않은 파일명입니다."),
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "파일 또는 디렉토리를 찾을 수 없습니다."),
    FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "파일 크기가 허용 한도를 초과했습니다."),
    BINARY_FILE_EDIT_FORBIDDEN(HttpStatus.BAD_REQUEST, "바이너리 파일은 편집할 수 없습니다."),
    SFTP_OPERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "파일 작업에 실패했습니다.");

    private final HttpStatus status;
    private final String message;
}
