package gj.cloud.ops.application.deployment.spec;

import gj.cloud.ops.application.deployment.dto.ComposeArtifact;
import gj.cloud.ops.application.deployment.routing.ComposeRouterPlanner;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DeploymentSpecRendererTest {

    private final DeploymentSpecRenderer renderer =
            new DeploymentSpecRenderer(mock(DockerfileGenerator.class), new ComposeRouterPlanner());

    @Test
    void preservesCustomSubdomainInRenderedDeploymentRoute() {
        ServiceSpec service = new ServiceSpec(
                "web",
                DeploymentMode.SERVICE,
                new BuildSpec(RuntimeKind.DOCKERFILE, null, BuildRunStrategy.DOCKERFILE, null),
                new ArtifactSpec(ArtifactType.CONTAINER_IMAGE, "."),
                new RunSpec(RuntimeKind.DOCKERFILE, BuildRunStrategy.DOCKERFILE, 3000),
                ".",
                new ExposeSpec(true, "http", "/", "portfolio", null, null, null));
        DeploymentSpec spec = new DeploymentSpec("2.0", List.of(service), List.of(), "app-network", false);

        ComposeArtifact artifact = renderer.render(spec);

        assertThat(artifact.exposedRoutes()).singleElement()
                .satisfies(route -> assertThat(route.customSubdomain()).isEqualTo("portfolio"));
    }

    @Test
    void addsSingleCaddyGatewayForMultipleHttpServices() {
        ServiceSpec web = service("web", 3000, "/");
        ServiceSpec api = service("api", 8080, "/health");
        DeploymentSpec spec = new DeploymentSpec(
                "2.0", List.of(web, api), List.of(), "app-network", false);

        ComposeArtifact artifact = renderer.render(spec);

        assertThat(artifact.composeContent())
                .contains("gamjabox-router")
                .contains("caddy:2.10-alpine")
                .contains("reverse_proxy web:3000")
                .contains("reverse_proxy api:8080");
        assertThat(artifact.exposedRoutes()).singleElement()
                .satisfies(route -> {
                    assertThat(route.serviceName()).isEqualTo("gamjabox-router");
                    assertThat(route.nickname()).isEqualTo("gateway");
                });
        assertThat(artifact.healthChecks())
                .extracting(check -> check.serviceName() + ":" + check.path())
                .contains("gamjabox-router:/", "gamjabox-router:/api/health");
    }

    @Test
    void preservesTcpRouteWhenHttpServicesAreCombinedBehindCaddy() {
        ServiceSpec web = service("web", 3000, "/");
        ServiceSpec api = service("api", 8080, "/health");
        ServiceSpec socket = new ServiceSpec(
                "socket",
                DeploymentMode.SERVICE,
                new BuildSpec(RuntimeKind.DOCKERFILE, null, BuildRunStrategy.DOCKERFILE, null),
                new ArtifactSpec(ArtifactType.CONTAINER_IMAGE, "."),
                new RunSpec(RuntimeKind.DOCKERFILE, BuildRunStrategy.DOCKERFILE, 9000),
                "socket",
                new ExposeSpec(true, "tcp", null, null, null, null, null));
        DeploymentSpec spec = new DeploymentSpec(
                "2.0", List.of(web, api, socket), List.of(), "app-network", false);

        ComposeArtifact artifact = renderer.render(spec);

        assertThat(artifact.exposedRoutes())
                .extracting(route -> route.serviceName() + ":" + route.protocol())
                .containsExactly("gamjabox-router:HTTP", "socket:TCP");
        assertThat(artifact.composeContent())
                .contains("gamjabox.router.enabled: false")
                .contains("9000:9000");
    }

    @Test
    void exposesDomainModeServiceOnItsOwnSubdomainBehindSingleRouterPort() {
        ServiceSpec web = service("web", 3000, "/");
        ServiceSpec community = new ServiceSpec(
                "community",
                DeploymentMode.SERVICE,
                new BuildSpec(RuntimeKind.DOCKERFILE, null, BuildRunStrategy.DOCKERFILE, null),
                new ArtifactSpec(ArtifactType.CONTAINER_IMAGE, "."),
                new RunSpec(RuntimeKind.DOCKERFILE, BuildRunStrategy.DOCKERFILE, 8082),
                "community",
                new ExposeSpec(true, "http", "/", "community", null, null, "DOMAIN"));
        DeploymentSpec spec = new DeploymentSpec(
                "2.0", List.of(web, community), List.of(), "app-network", false);

        ComposeArtifact artifact = renderer.render(spec);

        assertThat(artifact.composeContent())
                .contains("host_regexp ^community\\.")
                .contains("reverse_proxy community:8082");
        // 게이트웨이(기본 도메인)와 도메인 모드 서비스가 하나의 router 호스트 포트를 공유한다.
        int routerPort = artifact.exposedRoutes().stream()
                .filter(route -> route.serviceName().equals("gamjabox-router"))
                .map(route -> route.port())
                .findFirst()
                .orElseThrow();
        assertThat(artifact.exposedRoutes())
                .allSatisfy(route -> assertThat(route.port()).isEqualTo(routerPort));
        assertThat(artifact.exposedRoutes())
                .anySatisfy(route -> {
                    assertThat(route.serviceName()).isEqualTo("community");
                    assertThat(route.customSubdomain()).isEqualTo("community");
                });
    }

    private ServiceSpec service(String name, int port, String healthPath) {
        return new ServiceSpec(
                name,
                DeploymentMode.SERVICE,
                new BuildSpec(RuntimeKind.DOCKERFILE, null, BuildRunStrategy.DOCKERFILE, null),
                new ArtifactSpec(ArtifactType.CONTAINER_IMAGE, "."),
                new RunSpec(RuntimeKind.DOCKERFILE, BuildRunStrategy.DOCKERFILE, port),
                name,
                new ExposeSpec(true, "http", healthPath, null, null, null, null));
    }
}
