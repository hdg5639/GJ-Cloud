package gj.cloud.ops.application.deployment.repoanalysis;

public record DetectedComposeFile(
        String path,
        String directory,
        String content,
        long sizeBytes,
        boolean primary
) {
}
