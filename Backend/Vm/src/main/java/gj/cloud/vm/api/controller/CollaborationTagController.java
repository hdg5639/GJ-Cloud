package gj.cloud.vm.api.controller;

import gj.cloud.vm.application.collab.dto.TagResponse;
import gj.cloud.vm.application.collab.service.CollaborationService;
import gj.cloud.vm.domain.collab.enums.ScopeType;
import gj.cloud.vm.global.response.ApiResponse;
import gj.cloud.vm.global.security.VmPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Tag(name = "CollaborationTag", description = "협업 태그 API")
@RestController
@RequestMapping("/vms/collaboration-tags")
@RequiredArgsConstructor
public class CollaborationTagController {

    private final CollaborationService collaborationService;

    @Operation(summary = "태그 자동완성 검색")
    @GetMapping
    public Mono<ApiResponse<List<TagResponse>>> searchTags(
            @AuthenticationPrincipal VmPrincipal principal,
            @RequestParam ScopeType scopeType,
            @RequestParam UUID scopeId,
            @RequestParam(required = false) String query) {
        return collaborationService.searchTags(scopeType, scopeId, query)
                .collectList().map(ApiResponse::ok);
    }

    @Operation(summary = "태그 전체 목록 (관리용)")
    @GetMapping("/all")
    public Mono<ApiResponse<List<TagResponse>>> listTags(
            @AuthenticationPrincipal VmPrincipal principal,
            @RequestParam ScopeType scopeType,
            @RequestParam UUID scopeId) {
        return collaborationService.listTags(scopeType, scopeId, principal.userId(), principal.email())
                .collectList().map(ApiResponse::ok);
    }

    @Operation(summary = "태그 삭제 (OWNER, ADMIN)")
    @DeleteMapping("/{tagId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteTag(
            @AuthenticationPrincipal VmPrincipal principal,
            @PathVariable UUID tagId) {
        return collaborationService.deleteTag(tagId, principal.userId(), principal.email());
    }
}
