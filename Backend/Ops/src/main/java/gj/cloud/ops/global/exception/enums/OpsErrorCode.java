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
    SFTP_OPERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "파일 작업에 실패했습니다."),

    // 배포 파이프라인
    SSH_COMMAND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "원격 명령 실행에 실패했습니다."),
    SSH_COMMAND_TIMEOUT(HttpStatus.INTERNAL_SERVER_ERROR, "원격 명령 실행이 시간 초과되었습니다."),
    INVALID_COMPOSE(HttpStatus.BAD_REQUEST, "compose 검증에 실패했습니다."),
    INVALID_REPO_CONFIG(HttpStatus.BAD_REQUEST, "레포 URL 또는 브랜치명이 유효하지 않습니다."),
    DEPLOYMENT_IN_PROGRESS(HttpStatus.CONFLICT, "이미 배포가 진행 중입니다."),
    DEPLOYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "배포 이력을 찾을 수 없습니다."),

    // Docker 관리
    DOCKER_NOT_INSTALLED(HttpStatus.BAD_REQUEST, "VM에 Docker가 설치되어 있지 않습니다."),
    DOCKER_INSTALL_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Docker 설치에 실패했습니다."),
    DOCKER_COMMAND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Docker 명령 실행에 실패했습니다."),
    INVALID_DOCKER_IDENTIFIER(HttpStatus.BAD_REQUEST, "유효하지 않은 식별자입니다."),

    // AI 배포 스펙 생성 (D-3)
    AI_SPEC_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "AI 배포 스펙 생성 요청에 실패했습니다."),
    AI_SPEC_INVALID_RESPONSE(HttpStatus.INTERNAL_SERVER_ERROR, "AI가 생성한 스펙이 올바르지 않습니다.");

    private final HttpStatus status;
    private final String message;
}
