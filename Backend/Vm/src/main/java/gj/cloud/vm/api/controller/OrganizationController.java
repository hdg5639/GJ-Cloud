package gj.cloud.vm.api.controller;

import gj.cloud.vm.application.org.dto.*;
import gj.cloud.vm.application.org.service.OrganizationService;
import gj.cloud.vm.global.response.ApiResponse;
import gj.cloud.vm.global.security.VmPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Tag(name = "Organization", description = "조직 관리 API")
@RestController
@RequestMapping("/vms/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;

    @Operation(summary = "Organization 생성")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ApiResponse<OrgDetailResponse>> create(
            @AuthenticationPrincipal VmPrincipal principal,
            ServerWebExchange exchange,
            @Valid @RequestBody OrgCreateRequest request) {
        return organizationService.create(principal.userId(), principal.email(), extractToken(exchange), request)
                .map(ApiResponse::ok);
    }

    @Operation(summary = "내 Organization 목록")
    @GetMapping
    public Mono<ApiResponse<List<OrgResponse>>> list(@AuthenticationPrincipal VmPrincipal principal) {
        return organizationService.listMyOrganizations(principal.email()).collectList().map(ApiResponse::ok);
    }

    @Operation(summary = "받은 초대 목록")
    @GetMapping("/invitations")
    public Mono<ApiResponse<List<OrgResponse>>> invitations(@AuthenticationPrincipal VmPrincipal principal) {
        return organizationService.listPendingInvitations(principal.email()).collectList().map(ApiResponse::ok);
    }

    @Operation(summary = "Organization 상세")
    @GetMapping("/{orgId}")
    public Mono<ApiResponse<OrgDetailResponse>> get(
            @AuthenticationPrincipal VmPrincipal principal,
            ServerWebExchange exchange,
            @PathVariable UUID orgId) {
        return organizationService.getDetail(orgId, principal.email(), extractToken(exchange)).map(ApiResponse::ok);
    }

    @Operation(summary = "Organization 이름 변경 (OWNER)")
    @PatchMapping("/{orgId}")
    public Mono<ApiResponse<OrgDetailResponse>> update(
            @AuthenticationPrincipal VmPrincipal principal,
            @PathVariable UUID orgId,
            @Valid @RequestBody OrgUpdateRequest request) {
        return organizationService.update(orgId, principal.email(), request).map(ApiResponse::ok);
    }

    @Operation(summary = "Organization 삭제 (OWNER)")
    @DeleteMapping("/{orgId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(
            @AuthenticationPrincipal VmPrincipal principal,
            @PathVariable UUID orgId) {
        return organizationService.delete(orgId, principal.email());
    }

    @Operation(summary = "팀원 초대 (OWNER, ADMIN)")
    @PostMapping("/{orgId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ApiResponse<MemberResponse>> invite(
            @AuthenticationPrincipal VmPrincipal principal,
            @PathVariable UUID orgId,
            @Valid @RequestBody MemberInviteRequest request) {
        return organizationService.invite(orgId, principal.email(), request).map(ApiResponse::ok);
    }

    @Operation(summary = "초대용 사용자 검색 (OWNER, ADMIN)", description = "닉네임/이메일 부분 일치로 최대 10건을 검색합니다.")
    @GetMapping("/{orgId}/members/search")
    public Mono<ApiResponse<List<MemberSearchResult>>> searchMembers(
            @AuthenticationPrincipal VmPrincipal principal,
            ServerWebExchange exchange,
            @PathVariable UUID orgId,
            @RequestParam String query) {
        return organizationService.searchMembers(orgId, principal.email(), extractToken(exchange), query).map(ApiResponse::ok);
    }

    @Operation(summary = "초대 수락/거절 (초대 대상자)")
    @PatchMapping("/{orgId}/members/{memberId}/respond")
    public Mono<ApiResponse<MemberResponse>> respond(
            @AuthenticationPrincipal VmPrincipal principal,
            @PathVariable UUID orgId,
            @PathVariable UUID memberId,
            @RequestBody MemberRespondRequest request) {
        return organizationService.respond(orgId, memberId, principal.userId(), principal.email(), request).map(ApiResponse::ok);
    }

    @Operation(summary = "팀원 제거 (OWNER, ADMIN) 또는 본인 탈퇴")
    @DeleteMapping("/{orgId}/members/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> removeMember(
            @AuthenticationPrincipal VmPrincipal principal,
            @PathVariable UUID orgId,
            @PathVariable UUID memberId) {
        return organizationService.removeMember(orgId, memberId, principal.email());
    }

    @Operation(summary = "팀원 권한 변경 (OWNER)")
    @PatchMapping("/{orgId}/members/{memberId}/role")
    public Mono<ApiResponse<MemberResponse>> updateRole(
            @AuthenticationPrincipal VmPrincipal principal,
            @PathVariable UUID orgId,
            @PathVariable UUID memberId,
            @RequestBody MemberRoleUpdateRequest request) {
        return organizationService.updateMemberRole(orgId, memberId, principal.email(), request).map(ApiResponse::ok);
    }

    @Operation(summary = "VM 연결 (OWNER)")
    @PostMapping("/{orgId}/vms")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Void> addVm(
            @AuthenticationPrincipal VmPrincipal principal,
            @PathVariable UUID orgId,
            @RequestBody OrgVmRequest request) {
        return organizationService.addVm(orgId, principal.email(), request.vmId());
    }

    @Operation(summary = "VM 연결 해제 (OWNER)")
    @DeleteMapping("/{orgId}/vms/{vmId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> removeVm(
            @AuthenticationPrincipal VmPrincipal principal,
            @PathVariable UUID orgId,
            @PathVariable UUID vmId) {
        return organizationService.removeVm(orgId, principal.email(), vmId);
    }

    private String extractToken(ServerWebExchange exchange) {
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        return authHeader != null && authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
    }
}
