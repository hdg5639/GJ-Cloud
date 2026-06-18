package gj.cloud.vm.global.exception.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum VmErrorCode {
    VM_NOT_FOUND(HttpStatus.NOT_FOUND, "VM을 찾을 수 없습니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    VM_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "VM 생성 한도를 초과했습니다."),
    SSH_KEY_FETCH_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "SSH 키 조회에 실패했습니다."),
    PROXMOX_CLONE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "VM 생성에 실패했습니다."),
    PROXMOX_DELETE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "VM 삭제에 실패했습니다."),
    VMID_ALLOCATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "VM ID 할당에 실패했습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    INVALID_AUDIENCE(HttpStatus.UNAUTHORIZED, "이 서비스에 유효하지 않은 토큰입니다."),
    IP_POOL_EXHAUSTED(HttpStatus.SERVICE_UNAVAILABLE, "사용 가능한 IP가 없습니다. 잠시 후 다시 시도해주세요."),
    CLOUDFLARE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Cloudflare 연동에 실패했습니다.");

    private final HttpStatus status;
    private final String message;
}
