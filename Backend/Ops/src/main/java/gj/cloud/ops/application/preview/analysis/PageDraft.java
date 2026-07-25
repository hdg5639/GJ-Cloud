package gj.cloud.ops.application.preview.analysis;

import java.util.List;

public record PageDraft(
        String id,
        String title,
        PageSkeletonType skeleton,
        List<String> capabilityIds
) {
}
