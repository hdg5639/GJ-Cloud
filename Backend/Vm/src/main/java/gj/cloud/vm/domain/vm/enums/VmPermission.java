package gj.cloud.vm.domain.vm.enums;

public enum VmPermission {
    TERMINAL_ACCESS,
    FILE_READ,
    FILE_WRITE,
    DEPLOY,
    // DB 백업 이력·다운로드·무결성 검증은 일반 파일 조회와 분리한다.
    BACKUP_READ,
    // Docker 제어(시작/정지/삭제/설치)는 VM root 권한과 동급이라 DOCKER_ADMIN만 별도 — 조회는 DOCKER_READ로 MEMBER도 허용 (C.5)
    DOCKER_READ,
    DOCKER_ADMIN,
    // AUTHZ-001: 컨테이너 로그는 DOCKER_READ(목록/상태 조회)와 달리 애플리케이션 시크릿(DB 접속정보,
    // 스택트레이스 속 토큰 등)이 그대로 노출될 수 있어 별도 권한으로 분리
    DOCKER_LOG_READ,
    // AUTHZ-001: 배포된 앱의 .env, SSH/자격증명 디렉토리 등 민감 파일의 "내용"을 읽으려면 FILE_READ와
    // 별도로 이 권한이 필요(파일/디렉토리 존재 자체를 보는 건 FILE_READ로 충분 — SftpPathResolver 참고)
    SECRET_READ
}
