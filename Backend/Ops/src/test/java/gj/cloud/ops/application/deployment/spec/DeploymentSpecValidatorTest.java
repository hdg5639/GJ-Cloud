package gj.cloud.ops.application.deployment.spec;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentSpecValidatorTest {

    private final DeploymentSpecValidator validator = new DeploymentSpecValidator();

    private ServiceSpec staticService(String name, boolean expose) {
        return new ServiceSpec(name, DeploymentMode.ARTIFACT_ONLY,
                new BuildSpec(RuntimeKind.NONE, null, BuildRunStrategy.NONE, null),
                new ArtifactSpec(ArtifactType.STATIC_DIRECTORY, "."),
                new RunSpec(RuntimeKind.STATIC_SERVER, BuildRunStrategy.STATIC_SERVER, 80),
                ".", expose ? new ExposeSpec(true, "http", "/") : null);
    }

    @Test
    void validStaticSpecHasNoErrors() {
        DeploymentSpec spec = new DeploymentSpec("2.0", List.of(staticService("web", true)), List.of(), "app-network");
        assertThat(validator.collectErrors(spec)).isEmpty();
    }

    @Test
    void rejectsUnsupportedSchemaVersion() {
        DeploymentSpec spec = new DeploymentSpec("1.0", List.of(staticService("web", true)), List.of(), "app-network");
        assertThat(validator.collectErrors(spec)).anyMatch(e -> e.contains("schemaVersion"));
    }

    @Test
    void rejectsExposeEnabledWithoutContainerPort() {
        ServiceSpec service = new ServiceSpec("web", DeploymentMode.SERVICE,
                new BuildSpec(RuntimeKind.NODEJS, null, BuildRunStrategy.NONE, null),
                new ArtifactSpec(ArtifactType.CONTAINER_IMAGE, "."),
                new RunSpec(RuntimeKind.NODEJS, BuildRunStrategy.NPM_START, null),
                ".", new ExposeSpec(true, "http", "/"));
        DeploymentSpec spec = new DeploymentSpec("2.0", List.of(service), List.of(), "app-network");
        assertThat(validator.collectErrors(spec)).anyMatch(e -> e.contains("containerPort"));
    }

    @Test
    void rejectsHealthCheckPathWhenExposeDisabled() {
        ServiceSpec service = new ServiceSpec("web", DeploymentMode.ARTIFACT_ONLY,
                new BuildSpec(RuntimeKind.NONE, null, BuildRunStrategy.NONE, null),
                new ArtifactSpec(ArtifactType.STATIC_DIRECTORY, "."),
                new RunSpec(RuntimeKind.STATIC_SERVER, BuildRunStrategy.STATIC_SERVER, 80),
                ".", new ExposeSpec(false, "http", "/health"));
        DeploymentSpec spec = new DeploymentSpec("2.0", List.of(service), List.of(), "app-network");
        assertThat(validator.collectErrors(spec)).anyMatch(e -> e.contains("healthCheckPath"));
    }

    @Test
    void rejectsMismatchedBuildRuntimeAndStrategy() {
        // build.runtime=PYTHON인데 build.strategy가 Node 전용(NPM_BUILD)인 불일치 케이스
        ServiceSpec service = new ServiceSpec("api", DeploymentMode.SERVICE,
                new BuildSpec(RuntimeKind.PYTHON, "3.11", BuildRunStrategy.NPM_BUILD, null),
                new ArtifactSpec(ArtifactType.PYTHON_APPLICATION, "."),
                new RunSpec(RuntimeKind.PYTHON, BuildRunStrategy.UVICORN, 8000),
                ".", null);
        DeploymentSpec spec = new DeploymentSpec("2.0", List.of(service), List.of(), "app-network");
        assertThat(validator.collectErrors(spec)).anyMatch(e -> e.contains("build.strategy"));
    }

    @Test
    void artifactOnlyRejectsApplicationServerRunStrategy() {
        ServiceSpec service = new ServiceSpec("web", DeploymentMode.ARTIFACT_ONLY,
                new BuildSpec(RuntimeKind.JAVA, "21", BuildRunStrategy.GRADLE_BOOT_JAR, null),
                new ArtifactSpec(ArtifactType.JAR, "build/libs"),
                new RunSpec(RuntimeKind.JAVA, BuildRunStrategy.JAVA_JAR, 8080),
                ".", null);
        DeploymentSpec spec = new DeploymentSpec("2.0", List.of(service), List.of(), "app-network");
        assertThat(validator.collectErrors(spec)).anyMatch(e -> e.contains("ARTIFACT_ONLY"));
    }
}
