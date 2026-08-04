package gj.cloud.user.application.support.dto;

import gj.cloud.user.domain.support.enums.SupportInquiryStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminSupportInquiryUpdateRequest(
        @NotNull SupportInquiryStatus status,
        @Size(max = 4_000) String response
) {
}
