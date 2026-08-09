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
    OPS_KEY_ISSUE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Ops 관리 키 발급에 실패했습니다."),
    SSH_PROVISIONING_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "VM SSH 초기화에 실패했습니다."),
    PROXMOX_CLONE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "VM 생성에 실패했습니다."),
    PROXMOX_DELETE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "VM 삭제에 실패했습니다."),
    VMID_ALLOCATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "VM ID 할당에 실패했습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    INVALID_AUDIENCE(HttpStatus.UNAUTHORIZED, "이 서비스에 유효하지 않은 토큰입니다."),
    CLOUDFLARE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Cloudflare 연동에 실패했습니다."),
    INVALID_DISK_SIZE(HttpStatus.BAD_REQUEST, "디스크 크기가 플랜 허용 범위를 벗어났습니다."),
    DISK_DOWNSIZE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "디스크 크기는 줄일 수 없습니다."),
    DOWNGRADE_DISK_TOO_LARGE(HttpStatus.BAD_REQUEST, "현재 디스크 크기가 너무 커서 다운그레이드할 수 없습니다. 새 VM을 생성해 데이터를 이전해주세요."),
    PORT_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "VM당 추가 가능한 포트 개수를 초과했습니다."),
    EMAIL_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "등록 가능한 이메일 개수를 초과했습니다."),
    PORT_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 등록된 포트입니다."),
    PORT_NICKNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    INVALID_ROUTE_SPEC(HttpStatus.BAD_REQUEST, "배포 라우트의 protocol 또는 visibility 값이 유효하지 않습니다."),
    PORT_NOT_FOUND(HttpStatus.NOT_FOUND, "포트를 찾을 수 없습니다."),
    PORT_DEPLOYMENT_MANAGED(HttpStatus.BAD_REQUEST, "자동 배포가 관리하는 CNAME은 수동으로 연결할 수 없습니다."),
    PORT_DEPLOYMENT_LINK_CONFLICT(HttpStatus.CONFLICT, "이미 다른 배포 대상에 연결된 CNAME입니다."),
    OWNER_EMAIL_REMOVAL_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "소유자 이메일은 삭제할 수 없습니다."),
    VM_NOT_RUNNING(HttpStatus.BAD_REQUEST, "VM이 실행 중이 아니므로 메트릭을 조회할 수 없습니다."),
    CUSTOM_SUBDOMAIN_PRO_ONLY(HttpStatus.FORBIDDEN, "커스텀 서브도메인은 PRO 플랜 사용자만 이용할 수 있습니다."),
    SUBDOMAIN_RESERVED(HttpStatus.BAD_REQUEST, "예약된 서브도메인입니다."),
    SUBDOMAIN_ALREADY_TAKEN(HttpStatus.CONFLICT, "이미 사용 중인 서브도메인입니다."),
    INVALID_SYSTEM_WORKER_SPEC(HttpStatus.BAD_REQUEST, "허용되지 않은 시스템 워커 사양입니다."),
    SYSTEM_WORKER_ALREADY_EXISTS(HttpStatus.CONFLICT, "Auto Preview Worker VM이 이미 존재합니다."),
    SYSTEM_WORKER_IDENTITY_MISMATCH(HttpStatus.CONFLICT, "예약된 VMID에 다른 VM이 존재합니다."),

    // Organization
    ORGANIZATION_NOT_FOUND(HttpStatus.NOT_FOUND, "조직을 찾을 수 없습니다."),
    NOT_ORGANIZATION_MEMBER(HttpStatus.FORBIDDEN, "해당 조직의 멤버가 아닙니다."),
    ORGANIZATION_PERMISSION_DENIED(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    MEMBER_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 초대된 사용자입니다."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "멤버를 찾을 수 없습니다."),
    VM_ALREADY_IN_ORGANIZATION(HttpStatus.CONFLICT, "이미 조직에 추가된 VM입니다."),
    ORGANIZATION_VM_NOT_FOUND(HttpStatus.NOT_FOUND, "조직에 연결된 VM을 찾을 수 없습니다."),
    CANNOT_REMOVE_OWNER(HttpStatus.BAD_REQUEST, "조직 소유자는 제거할 수 없습니다."),
    INVITATION_NOT_PENDING(HttpStatus.BAD_REQUEST, "대기 중인 초대가 아닙니다."),

    // Collaboration
    COLLABORATION_NOT_FOUND(HttpStatus.NOT_FOUND, "협업 항목을 찾을 수 없습니다."),
    COLLABORATION_PERMISSION_DENIED(HttpStatus.FORBIDDEN, "협업 항목에 대한 권한이 없습니다."),
    COLLABORATION_NOT_REQUEST_TYPE(HttpStatus.BAD_REQUEST, "요청(REQUEST) 타입에만 해결 처리가 가능합니다."),
    TAG_NOT_FOUND(HttpStatus.NOT_FOUND, "태그를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}
