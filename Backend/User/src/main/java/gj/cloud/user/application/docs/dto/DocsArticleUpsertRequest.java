package gj.cloud.user.application.docs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record DocsArticleUpsertRequest(
        @Size(max = 120) String slug,
        @NotBlank @Size(max = 180) String title,
        @NotBlank @Size(max = 400) String summary,
        @NotBlank @Size(max = 80) String category,
        @Size(max = 500) String coverImageUrl,
        @NotBlank @Size(max = 200_000) String content,
        @Size(max = 12) List<@Size(max = 60) String> tags,
        boolean featured,
        int sortOrder
) {
}
