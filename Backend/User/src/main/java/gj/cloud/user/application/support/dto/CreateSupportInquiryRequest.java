package gj.cloud.user.application.support.dto;

import gj.cloud.user.domain.support.enums.SupportInquiryCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateSupportInquiryRequest(
        @NotNull SupportInquiryCategory category,
        @NotBlank @Size(max = 120) String title,
        @NotBlank @Size(max = 4_000) String content,
        @Size(max = 120) @Pattern(regexp = "^[a-z0-9-]*$") String sourceArticleSlug,
        @Size(max = 180) String sourceArticleTitle
) {
}
