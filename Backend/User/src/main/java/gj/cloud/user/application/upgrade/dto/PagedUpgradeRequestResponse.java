package gj.cloud.user.application.upgrade.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record PagedUpgradeRequestResponse(
    List<UpgradeRequestResponse> content,
    int totalPages,
    long totalElements
) {
    public static PagedUpgradeRequestResponse from(Page<UpgradeRequestResponse> page) {
        return new PagedUpgradeRequestResponse(
            page.getContent(),
            page.getTotalPages(),
            page.getTotalElements()
        );
    }
}
