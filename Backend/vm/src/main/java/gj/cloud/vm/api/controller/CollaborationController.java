package gj.cloud.vm.api.controller;

import gj.cloud.vm.application.collab.dto.*;
import gj.cloud.vm.application.collab.service.CollaborationService;
import gj.cloud.vm.domain.collab.enums.CollaborationType;
import gj.cloud.vm.domain.collab.enums.ScopeType;
import gj.cloud.vm.global.response.ApiResponse;
import gj.cloud.vm.global.security.VmPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Tag(name = "Collaboration", description = "협업 공간 API")
@RestController
@RequiredArgsConstructor
public class CollaborationController {

    private final CollaborationService collaborationService;

    @Operation(summary = "협업 항목 목록 조회")
    @GetMapping("/collaborations")
    public Mono<ApiResponse<List<CollaborationResponse>>> list(
            @AuthenticationPrincipal VmPrincipal principal,
            @RequestParam ScopeType scopeType,
            @RequestParam UUID scopeId,
            @RequestParam(required = false) CollaborationType type) {
        return collaborationService.list(scopeType, scopeId, type, principal.userId(), principal.email())
                .collectList().map(ApiResponse::ok);
    }

    @Operation(summary = "협업 항목 단건 조회")
    @GetMapping("/collaborations/{id}")
    public Mono<ApiResponse<CollaborationResponse>> get(
            @AuthenticationPrincipal VmPrincipal principal,
            @PathVariable UUID id) {
        return collaborationService.get(id, principal.userId(), principal.email()).map(ApiResponse::ok);
    }

    @Operation(summary = "협업 항목 작성")
    @PostMapping("/collaborations")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ApiResponse<CollaborationResponse>> create(
            @AuthenticationPrincipal VmPrincipal principal,
            @Valid @RequestBody CollaborationCreateRequest request) {
        return collaborationService.create(request, principal.userId(), principal.email()).map(ApiResponse::ok);
    }

    @Operation(summary = "협업 항목 수정")
    @PatchMapping("/collaborations/{id}")
    public Mono<ApiResponse<CollaborationResponse>> update(
            @AuthenticationPrincipal VmPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody CollaborationUpdateRequest request) {
        return collaborationService.update(id, request, principal.userId(), principal.email()).map(ApiResponse::ok);
    }

    @Operation(summary = "협업 항목 삭제")
    @DeleteMapping("/collaborations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(
            @AuthenticationPrincipal VmPrincipal principal,
            @PathVariable UUID id) {
        return collaborationService.delete(id, principal.userId(), principal.email());
    }

    @Operation(summary = "고정 토글 (OWNER, ADMIN)")
    @PatchMapping("/collaborations/{id}/pin")
    public Mono<ApiResponse<CollaborationResponse>> pin(
            @AuthenticationPrincipal VmPrincipal principal,
            @PathVariable UUID id) {
        return collaborationService.pin(id, principal.userId(), principal.email()).map(ApiResponse::ok);
    }

    @Operation(summary = "요청 해결 처리 (REQUEST 타입만)")
    @PatchMapping("/collaborations/{id}/resolve")
    public Mono<ApiResponse<CollaborationResponse>> resolve(
            @AuthenticationPrincipal VmPrincipal principal,
            @PathVariable UUID id) {
        return collaborationService.resolve(id, principal.userId(), principal.email()).map(ApiResponse::ok);
    }

    @Operation(summary = "태그 자동완성 검색")
    @GetMapping("/collaboration-tags")
    public Mono<ApiResponse<List<TagResponse>>> searchTags(
            @AuthenticationPrincipal VmPrincipal principal,
            @RequestParam ScopeType scopeType,
            @RequestParam UUID scopeId,
            @RequestParam(required = false) String query) {
        return collaborationService.searchTags(scopeType, scopeId, query)
                .collectList().map(ApiResponse::ok);
    }

    @Operation(summary = "태그 전체 목록 (관리용)")
    @GetMapping("/collaboration-tags/all")
    public Mono<ApiResponse<List<TagResponse>>> listTags(
            @AuthenticationPrincipal VmPrincipal principal,
            @RequestParam ScopeType scopeType,
            @RequestParam UUID scopeId) {
        return collaborationService.listTags(scopeType, scopeId, principal.userId(), principal.email())
                .collectList().map(ApiResponse::ok);
    }

    @Operation(summary = "태그 삭제 (OWNER, ADMIN)")
    @DeleteMapping("/collaboration-tags/{tagId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteTag(
            @AuthenticationPrincipal VmPrincipal principal,
            @PathVariable UUID tagId) {
        return collaborationService.deleteTag(tagId, principal.userId(), principal.email());
    }
}
