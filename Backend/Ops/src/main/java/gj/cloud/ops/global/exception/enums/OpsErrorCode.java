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
    REPOSITORY_NETWORK_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE,
            "저장소 호스트에 연결할 수 없습니다. VM의 DNS/네트워크 상태를 확인한 뒤 다시 시도해주세요."),
    INVALID_COMPOSE(HttpStatus.BAD_REQUEST, "compose 검증에 실패했습니다."),
    INVALID_REPO_CONFIG(HttpStatus.BAD_REQUEST, "레포 URL 또는 브랜치명이 유효하지 않습니다."),
    DEPLOYMENT_IN_PROGRESS(HttpStatus.CONFLICT, "이미 배포가 진행 중입니다."),
    DEPLOYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "배포 이력을 찾을 수 없습니다."),
    DEPLOYMENT_ROLLBACK_TARGET_NOT_SUCCEEDED(HttpStatus.BAD_REQUEST, "성공한 배포로만 롤백할 수 있습니다."),
    DEPLOYMENT_TEARDOWN_TARGET_INVALID(HttpStatus.BAD_REQUEST, "현재 활성화된 배포만 내릴 수 있습니다."),
    DEPLOYMENT_TEARDOWN_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "배포 내리기에 실패했습니다."),
    DEPLOYMENT_TARGET_NOT_FOUND(HttpStatus.NOT_FOUND, "배포 대상을 찾을 수 없습니다."),
    DEPLOYMENT_TARGET_NAME_DUPLICATED(HttpStatus.CONFLICT, "같은 VM에 동일한 배포 대상 이름이 이미 있습니다."),
    AUTO_DEPLOY_REQUIRES_GITHUB(HttpStatus.BAD_REQUEST, "자동 배포는 GitHub App 저장소 연결이 필요합니다."),
    DEPLOYMENT_TARGET_DELETE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR,
            "배포 대상 삭제 중 VM 정리에 실패했습니다. VM 상태를 확인한 뒤 다시 시도해주세요."),
    DEPLOYMENT_CNAME_NOT_FOUND(HttpStatus.NOT_FOUND, "연결할 수동 CNAME을 찾을 수 없습니다."),
    DEPLOYMENT_CNAME_LINK_FAILED(HttpStatus.BAD_REQUEST, "수동 CNAME을 배포 대상에 연결하지 못했습니다."),

    // GitHub App / push webhook
    GITHUB_APP_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "GitHub App 연동이 설정되지 않았습니다."),
    GITHUB_INSTALLATION_NOT_FOUND(HttpStatus.NOT_FOUND, "연결된 GitHub App 설치를 찾을 수 없습니다."),
    GITHUB_REPOSITORY_NOT_FOUND(HttpStatus.NOT_FOUND, "GitHub App이 접근할 수 있는 저장소를 찾을 수 없습니다."),
    GITHUB_INSTALL_STATE_INVALID(HttpStatus.BAD_REQUEST, "GitHub 연결 요청이 만료됐거나 유효하지 않습니다."),
    GITHUB_API_FAILED(HttpStatus.BAD_GATEWAY, "GitHub API 요청에 실패했습니다."),
    GITHUB_WEBHOOK_SIGNATURE_INVALID(HttpStatus.UNAUTHORIZED, "GitHub 웹훅 서명이 유효하지 않습니다."),

    // Docker 관리
    DOCKER_NOT_INSTALLED(HttpStatus.BAD_REQUEST, "VM에 Docker가 설치되어 있지 않습니다."),
    DOCKER_INSTALL_IN_PROGRESS(HttpStatus.CONFLICT, "이미 Docker 설치가 진행 중입니다."),
    DOCKER_INSTALL_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Docker 설치에 실패했습니다."),
    DOCKER_COMMAND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Docker 명령 실행에 실패했습니다."),
    INVALID_DOCKER_IDENTIFIER(HttpStatus.BAD_REQUEST, "유효하지 않은 식별자입니다."),

    // AI 배포 스펙 생성 (D-3)
    AI_SPEC_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "AI 배포 스펙 생성 요청에 실패했습니다."),
    AI_SPEC_INVALID_RESPONSE(HttpStatus.INTERNAL_SERVER_ERROR, "AI가 생성한 스펙이 올바르지 않습니다."),
    AI_SPEC_NEEDS_INPUT(HttpStatus.UNPROCESSABLE_CONTENT, "자동 생성에 필요한 정보가 부족합니다."),
    AI_SPEC_UNSUPPORTED(HttpStatus.UNPROCESSABLE_CONTENT, "이 저장소 구성은 자동 배포를 지원하지 않습니다."),
    AI_SPEC_CONFLICT(HttpStatus.CONFLICT, "입력값이 저장소 분석 결과와 충돌합니다."),
    DEPLOYMENT_POLICY_VIOLATION(HttpStatus.BAD_REQUEST, "배포 스펙이 보안 정책을 위반했습니다."),

    // 저장소 분석 (AI 배포 스펙 생성 전 결정론적 사전 분석)
    LOCAL_COMMAND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "로컬 명령 실행에 실패했습니다."),
    LOCAL_COMMAND_TIMEOUT(HttpStatus.INTERNAL_SERVER_ERROR, "로컬 명령 실행이 시간 초과되었습니다."),
    REPOSITORY_CLONE_FAILED(HttpStatus.BAD_REQUEST, "저장소를 가져오지 못했습니다. URL/브랜치/PAT를 확인해주세요."),
    REPOSITORY_TOO_LARGE(HttpStatus.BAD_REQUEST, "저장소 크기가 분석 허용 한도를 초과했습니다."),

    // 수동 DB 백업 (11절)
    INVALID_DB_IDENTIFIER(HttpStatus.BAD_REQUEST, "유효하지 않은 DB 식별자입니다."),
    DB_BACKUP_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "DB 백업에 실패했습니다."),

    // Auto Preview — OpenAPI 결정론적 분석 (GamjaBox_2.0_Key_Features.md 1단계)
    INVALID_API_DOCS_URL(HttpStatus.BAD_REQUEST, "API 문서 URL이 유효하지 않습니다."),
    API_DOCS_FETCH_FAILED(HttpStatus.BAD_REQUEST, "API 문서를 가져오지 못했습니다. URL을 확인해주세요."),
    API_DOCS_TOO_LARGE(HttpStatus.BAD_REQUEST, "API 문서 크기가 허용 한도를 초과했습니다."),
    API_DOCS_PARSE_FAILED(HttpStatus.BAD_REQUEST, "API 문서를 해석하지 못했습니다. OpenAPI 3.x 형식인지 확인해주세요."),
    API_DOCS_UNSUPPORTED_VERSION(HttpStatus.BAD_REQUEST, "OpenAPI 3.x 문서만 지원합니다."),
    PREVIEW_API_SOURCE_REQUIRED(HttpStatus.BAD_REQUEST, "API 문서 URL 또는 OpenAPI 파일 중 하나를 입력해주세요."),
    PREVIEW_CAPABILITY_SELECTION_INVALID(HttpStatus.BAD_REQUEST, "선택한 API 기능을 현재 문서에서 찾지 못했습니다."),
    INVALID_PREVIEW_BLUEPRINT(HttpStatus.BAD_REQUEST, "Auto Preview Blueprint 검증에 실패했습니다."),

    // PRO Custom Scenario Builder
    CUSTOM_SCENARIO_PRO_REQUIRED(HttpStatus.PAYMENT_REQUIRED,
            "커스텀 시나리오 생성과 저장은 PRO 플랜에서 사용할 수 있습니다."),
    CUSTOM_SCENARIO_GENERATION_FAILED(HttpStatus.UNPROCESSABLE_CONTENT,
            "요청한 시나리오를 현재 API로 구성하지 못했습니다."),
    CUSTOM_SCENARIO_NOT_FOUND(HttpStatus.NOT_FOUND, "커스텀 시나리오를 찾을 수 없습니다."),
    CUSTOM_SCENARIO_INVALID(HttpStatus.UNPROCESSABLE_CONTENT,
            "검증을 통과한 커스텀 시나리오만 활성화할 수 있습니다."),

    // Scenario Regression & Automation
    REGRESSION_SUITE_NOT_FOUND(HttpStatus.NOT_FOUND, "회귀 테스트 스위트를 찾을 수 없습니다."),
    REGRESSION_SUITE_INVALID(HttpStatus.UNPROCESSABLE_CONTENT,
            "실행 가능한 커스텀 시나리오만 회귀 테스트 스위트에 포함할 수 있습니다."),
    REGRESSION_RUN_NOT_FOUND(HttpStatus.NOT_FOUND, "회귀 테스트 실행 이력을 찾을 수 없습니다."),
    REGRESSION_TARGET_URL_INVALID(HttpStatus.BAD_REQUEST,
            "회귀 테스트 대상 URL이 유효하지 않거나 내부 네트워크를 가리킵니다.");

    private final HttpStatus status;
    private final String message;
}
