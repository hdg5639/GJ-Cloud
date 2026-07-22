package gj.cloud.ops.application.deployment.spec;

import gj.cloud.ops.application.deployment.dto.ComposeArtifact;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DeploymentSpecRendererTest {

    private final DeploymentSpecRenderer renderer = new DeploymentSpecRenderer(mock(DockerfileGenerator.class));

    @Test
    void preservesCustomSubdomainInRenderedDeploymentRoute() {
        ServiceSpec service = new ServiceSpec(
                "web",
                DeploymentMode.SERVICE,
                new BuildSpec(RuntimeKind.DOCKERFILE, null, BuildRunStrategy.DOCKERFILE, null),
                new ArtifactSpec(ArtifactType.CONTAINER_IMAGE, "."),
                new RunSpec(RuntimeKind.DOCKERFILE, BuildRunStrategy.DOCKERFILE, 3000),
                ".",
                new ExposeSpec(true, "http", "/", "portfolio"));
        DeploymentSpec spec = new DeploymentSpec("2.0", List.of(service), List.of(), "app-network", false);

        ComposeArtifact artifact = renderer.render(spec);

        assertThat(artifact.exposedRoutes()).singleElement()
                .satisfies(route -> assertThat(route.customSubdomain()).isEqualTo("portfolio"));
    }
}
