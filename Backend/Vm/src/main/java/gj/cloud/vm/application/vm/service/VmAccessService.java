package gj.cloud.vm.application.vm.service;

import gj.cloud.vm.application.vm.dto.VmAccessContext;
import gj.cloud.vm.domain.org.enums.MemberRole;
import gj.cloud.vm.domain.org.repository.OrganizationMemberRepository;
import gj.cloud.vm.domain.org.repository.OrganizationVmRepository;
import gj.cloud.vm.domain.vm.enums.VmPermission;
import gj.cloud.vm.global.exception.VmException;
import gj.cloud.vm.global.exception.enums.VmErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

// VmServiceImpl/PortServiceImpl에 중복돼 있던 VM 접근 권한 판정 로직을 단일화.
// Ops 등 다른 서비스도 이 컴포넌트를 통해서만 VM 권한을 질의함 (VM 서비스가 단일 진실 공급원).
@Component
@RequiredArgsConstructor
public class VmAccessService {

    private final OrganizationVmRepository orgVmRepository;
    private final OrganizationMemberRepository orgMemberRepository;

    // 소유자이거나 해당 VM이 연결된 org의 ACCEPTED 멤버면 통과
    public Mono<Void> checkVmReadAccess(UUID vmId, String ownerId, String requesterId, String requesterEmail) {
        if (ownerId.equals(requesterId)) return Mono.empty();
        return orgVmRepository.findAllByVmId(vmId)
                .flatMap(orgVm -> orgMemberRepository.findAcceptedByOrgIdAndEmail(orgVm.getOrganizationId(), requesterEmail))
                .next()
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.FORBIDDEN)))
                .then();
    }

    // 소유자이거나 해당 VM이 연결된 org의 ACCEPTED ADMIN/OWNER 멤버면 통과
    public Mono<Void> checkVmAdminAccess(UUID vmId, String ownerId, String requesterId, String requesterEmail) {
        if (ownerId.equals(requesterId)) return Mono.empty();
        return orgVmRepository.findAllByVmId(vmId)
                .flatMap(orgVm -> orgMemberRepository.findAcceptedAdminByOrgIdAndEmail(orgVm.getOrganizationId(), requesterEmail))
                .next()
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.FORBIDDEN)))
                .then();
    }

    // Ops 등 내부 API가 사용할 role + permission 판정 (VM 소유자 = OWNER, org ADMIN/OWNER = ADMIN, org MEMBER = MEMBER)
    public Mono<VmAccessContext> resolveContext(UUID vmId, String ownerId, String requesterId, String requesterEmail) {
        if (ownerId.equals(requesterId)) {
            return Mono.just(new VmAccessContext(MemberRole.OWNER, permissionsFor(MemberRole.OWNER)));
        }
        return orgVmRepository.findAllByVmId(vmId)
                .flatMap(orgVm -> orgMemberRepository.findAcceptedByOrgIdAndEmail(orgVm.getOrganizationId(), requesterEmail))
                .next()
                .switchIfEmpty(Mono.error(new VmException(VmErrorCode.FORBIDDEN)))
                .map(member -> {
                    MemberRole role = member.getRole() == MemberRole.MEMBER ? MemberRole.MEMBER : MemberRole.ADMIN;
                    return new VmAccessContext(role, permissionsFor(role));
                });
    }

    // 셸 접근(TERMINAL_ACCESS)·Docker 제어(DOCKER_ADMIN)는 VM root 권한과 동급이므로 Owner/Admin만 허용.
    // Member는 조회(FILE_READ, DOCKER_READ)만 — Docker 컨테이너/이미지 목록은 볼 수 있지만 시작/정지/삭제는 불가 (C.5)
    // AUTHZ-001: 컨테이너 로그(DOCKER_LOG_READ)와 민감 파일 내용(SECRET_READ)은 시크릿 노출 위험이 있어
    // 기본 조회 권한과 분리 — Member는 여전히 부여받지 않음
    private Set<VmPermission> permissionsFor(MemberRole role) {
        if (role == MemberRole.MEMBER) {
            return EnumSet.of(VmPermission.FILE_READ, VmPermission.DOCKER_READ);
        }
        return EnumSet.of(
                VmPermission.TERMINAL_ACCESS,
                VmPermission.FILE_READ,
                VmPermission.FILE_WRITE,
                VmPermission.DEPLOY,
                VmPermission.DOCKER_READ,
                VmPermission.DOCKER_ADMIN,
                VmPermission.DOCKER_LOG_READ,
                VmPermission.SECRET_READ
        );
    }
}
