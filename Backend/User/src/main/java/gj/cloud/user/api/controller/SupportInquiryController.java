package gj.cloud.user.api.controller;

import gj.cloud.user.application.support.dto.CreateSupportInquiryRequest;
import gj.cloud.user.application.support.dto.SupportInquiryResponse;
import gj.cloud.user.application.support.service.SupportInquiryService;
import gj.cloud.user.global.response.ApiResponse;
import gj.cloud.user.global.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Support Inquiry", description = "사용자 문의 접수와 조회")
@RestController
@RequestMapping("/users/support-inquiries")
@RequiredArgsConstructor
public class SupportInquiryController {

    private final SupportInquiryService service;

    @Operation(
            summary = "문의 접수",
            description = "인증된 사용자의 이메일로 문의를 접수합니다. 설명서에서 시작한 경우 문서 slug와 제목을 함께 저장할 수 있습니다."
    )
    @PostMapping
    public ApiResponse<SupportInquiryResponse> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateSupportInquiryRequest request
    ) {
        return ApiResponse.ok(service.create(principal.userId(), principal.email(), request));
    }

    @Operation(
            summary = "내 문의 목록 조회",
            description = "현재 사용자가 접수한 문의와 관리자 답변을 최신순으로 조회합니다. page는 1부터 시작하고 size는 최대 100입니다."
    )
    @GetMapping
    public ApiResponse<Page<SupportInquiryResponse>> listMine(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(service.listMine(principal.userId(), page, size));
    }

    @Operation(summary = "내 문의 종료", description = "현재 사용자가 소유한 문의를 종료 상태로 변경합니다.")
    @PatchMapping("/{inquiryId}/close")
    public ApiResponse<SupportInquiryResponse> closeMine(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID inquiryId
    ) {
        return ApiResponse.ok(service.closeMine(principal.userId(), inquiryId));
    }
}
