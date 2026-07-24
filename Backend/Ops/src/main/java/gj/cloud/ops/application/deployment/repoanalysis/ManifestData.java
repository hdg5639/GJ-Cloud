package gj.cloud.ops.application.deployment.repoanalysis;

public record ManifestData(
        PackageJsonInfo packageJson,
        JavaBuildInfo javaBuild,
        PythonInfo python
) {
}
