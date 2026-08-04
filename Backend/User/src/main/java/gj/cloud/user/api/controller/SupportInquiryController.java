package gj.cloud.user.api.controller;

import gj.cloud.user.application.support.dto.CreateSupportInquiryRequest;
import gj.cloud.user.application.support.dto.SupportInquiryResponse;
import gj.cloud.user.application.support.service.SupportInquiryService;
import gj.cloud.user.global.response.ApiResponse;
import gj.cloud.user.global.security.UserPrincipal;
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

    @PostMapping
    public ApiResponse<SupportInquiryResponse> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateSupportInquiryRequest request
    ) {
        return ApiResponse.ok(service.create(principal.userId(), principal.email(), request));
    }

    @GetMapping
    public ApiResponse<Page<SupportInquiryResponse>> listMine(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(service.listMine(principal.userId(), page, size));
    }

    @PatchMapping("/{inquiryId}/close")
    public ApiResponse<SupportInquiryResponse> closeMine(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID inquiryId
    ) {
        return ApiResponse.ok(service.closeMine(principal.userId(), inquiryId));
    }
}
