package gj.cloud.vm.global.exception.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum VmErrorCode {
    VM_NOT_FOUND(HttpStatus.NOT_FOUND, "VM을 찾을 수 없습니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    ADMIN_ACCESS_REQUIRED(HttpStatus.FORBIDDEN, "관리자 권한이 필요합니다."),
    VM_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "VM 생성 한도를 초과했습니다."),
    SSH_KEY_FETCH_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "SSH 키 조회에 실패했습니다."),
    PROXMOX_CLONE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "VM 생성에 실패했습니다."),
    PROXMOX_DELETE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "VM 삭제에 실패했습니다."),
    VMID_ALLOCATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "VM ID 할당에 실패했습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    INVALID_AUDIENCE(HttpStatus.UNAUTHORIZED, "이 서비스에 유효하지 않은 토큰입니다."),
    IP_POOL_EXHAUSTED(HttpStatus.SERVICE_UNAVAILABLE, "사용 가능한 IP가 없습니다. 잠시 후 다시 시도해주세요."),
    CLOUDFLARE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Cloudflare 연동에 실패했습니다."),
    INVALID_DISK_SIZE(HttpStatus.BAD_REQUEST, "디스크 크기가 플랜 허용 범위를 벗어났습니다."),
    DISK_DOWNSIZE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "디스크 크기는 줄일 수 없습니다."),
    DOWNGRADE_DISK_TOO_LARGE(HttpStatus.BAD_REQUEST, "현재 디스크 크기가 너무 커서 다운그레이드할 수 없습니다. 새 VM을 생성해 데이터를 이전해주세요."),
    PORT_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "VM당 추가 가능한 포트 개수를 초과했습니다."),
    EMAIL_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "등록 가능한 이메일 개수를 초과했습니다."),
    PORT_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 등록된 포트입니다."),
    PORT_NICKNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    PORT_NOT_FOUND(HttpStatus.NOT_FOUND, "포트를 찾을 수 없습니다."),
    OWNER_EMAIL_REMOVAL_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "소유자 이메일은 삭제할 수 없습니다."),
    VM_NOT_RUNNING(HttpStatus.BAD_REQUEST, "VM이 실행 중이 아니므로 메트릭을 조회할 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}
