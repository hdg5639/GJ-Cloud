package gj.cloud.ops.application.deployment.repoanalysis;

public record JavaBuildInfo(
        boolean mavenProject,
        boolean gradleProject,
        boolean springBootDetected,
        boolean multiModule,
        Integer javaVersion,
        String packaging
) {
}
