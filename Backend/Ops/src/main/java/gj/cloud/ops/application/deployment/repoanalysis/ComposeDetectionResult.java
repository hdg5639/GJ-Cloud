package gj.cloud.ops.application.deployment.repoanalysis;

import java.util.List;

public record ComposeDetectionResult(
        boolean detected,
        String searchedContext,
        List<DetectedComposeFile> files,
        List<String> warnings
) {
}
