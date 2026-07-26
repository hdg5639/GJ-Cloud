package gj.cloud.ops.application.preview.analysis;

import java.util.List;

public record PageDraft(
        String id,
        String title,
        PageSkeletonType skeleton,
        List<String> capabilityIds
) {
    public PageDraft {
        capabilityIds = capabilityIds == null ? List.of() : List.copyOf(capabilityIds);
    }
}
