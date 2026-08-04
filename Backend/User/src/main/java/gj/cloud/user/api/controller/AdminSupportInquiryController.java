package gj.cloud.user.api.controller;

import gj.cloud.user.application.support.dto.AdminSupportInquiryUpdateRequest;
import gj.cloud.user.application.support.dto.SupportInquiryResponse;
import gj.cloud.user.application.support.service.SupportInquiryService;
import gj.cloud.user.domain.support.enums.SupportInquiryStatus;
import gj.cloud.user.global.response.ApiResponse;
import gj.cloud.user.global.security.UserPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Admin - Support Inquiry")
@RestController
@RequestMapping("/admin/users/support-inquiries")
@RequiredArgsConstructor
public class AdminSupportInquiryController {

    private final SupportInquiryService service;

    @GetMapping
    public ApiResponse<Page<SupportInquiryResponse>> list(
            @RequestParam(required = false) SupportInquiryStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(service.listAdmin(status, page, size));
    }

    @PatchMapping("/{inquiryId}")
    public ApiResponse<SupportInquiryResponse> update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID inquiryId,
            @Valid @RequestBody AdminSupportInquiryUpdateRequest request
    ) {
        return ApiResponse.ok(service.updateAdmin(principal.userId(), inquiryId, request));
    }
}
