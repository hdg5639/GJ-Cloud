package gj.cloud.ops.application.deployment.repoanalysis;

public record DetectedFiles(
        boolean dockerfile,
        boolean composeFile,
        boolean packageJson,
        boolean pomXml,
        boolean gradleBuild,
        boolean requirementsTxt,
        boolean pyprojectToml,
        boolean pipfile,
        boolean indexHtml
) {
}
