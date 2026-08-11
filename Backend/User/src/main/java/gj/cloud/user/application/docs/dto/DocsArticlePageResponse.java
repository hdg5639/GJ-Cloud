package gj.cloud.user.application.docs.dto;

import java.util.List;

public record DocsArticlePageResponse(
        DocsArticleResponse article,
        List<DocsNavigationItemResponse> sameCategory
) {
}
