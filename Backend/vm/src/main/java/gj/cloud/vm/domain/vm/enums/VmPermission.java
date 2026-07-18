package gj.cloud.vm.domain.vm.enums;

public enum VmPermission {
    TERMINAL_ACCESS,
    FILE_READ,
    FILE_WRITE,
    DEPLOY,
    // Docker 제어(시작/정지/삭제/설치)는 VM root 권한과 동급이라 DOCKER_ADMIN만 별도 — 조회는 DOCKER_READ로 MEMBER도 허용 (C.5)
    DOCKER_READ,
    DOCKER_ADMIN
}
