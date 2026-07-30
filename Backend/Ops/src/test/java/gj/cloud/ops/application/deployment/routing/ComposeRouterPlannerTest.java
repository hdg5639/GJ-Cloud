package gj.cloud.ops.application.deployment.routing;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ComposeRouterPlannerTest {

    private final ComposeRouterPlanner planner = new ComposeRouterPlanner();

    @Test
    void addsCaddyAndMovesApplicationPortsBehindRouter() {
        String compose = """
                services:
                  web:
                    build: ./web
                    ports:
                      - "3000:3000"
                    networks: [appnet]
                  api:
                    build: ./api
                    ports:
                      - "8080:8080"
                    networks:
                      appnet: {}
                  postgres:
                    image: postgres:17
                    expose: [5432]
                networks:
                  appnet: {}
                """;

        ComposeRouterPlanResult result = planner.plan(compose, null, Map.of());

        assertThat(result.status()).isEqualTo(ComposeRouterPlanResult.STATUS_ADDED);
        assertThat(result.routerHostPort()).isEqualTo(3000);
        assertThat(result.routerConfig())
                .contains("/__gamjabox_router_health")
                .contains("reverse_proxy web:3000")
                .contains("path /api /api/*")
                .contains("reverse_proxy api:8080");
        assertThat(result.routes()).extracting(ComposeRouterRoute::serviceName)
                .containsExactly("web", "api");

        Map<?, ?> root = new Yaml().load(result.enhancedComposeContent());
        Map<?, ?> services = (Map<?, ?>) root.get("services");
        Map<?, ?> web = (Map<?, ?>) services.get("web");
        Map<?, ?> api = (Map<?, ?>) services.get("api");
        Map<?, ?> router = (Map<?, ?>) services.get(ComposeRouterPlanner.ROUTER_SERVICE_NAME);
        assertThat(web.containsKey("ports")).isFalse();
        assertThat(web.containsKey("expose")).isTrue();
        assertThat(api.containsKey("ports")).isFalse();
        assertThat(api.containsKey("expose")).isTrue();
        assertThat(router.get("ports").toString()).contains("3000:8080");
        assertThat(services.containsKey("postgres")).isTrue();
        assertThat(root.containsKey("configs")).isTrue();
    }

    @Test
    void prefersNamedFrontendAsRootAndAvoidsRetainedPortCollision() {
        String compose = """
                services:
                  api:
                    ports:
                      - "8080:8080"
                      - "18080:9090"
                  frontend:
                    ports:
                      - "3000:3000"
                """;

        ComposeRouterPlanResult result = planner.plan(compose, 18080, Map.of());

        assertThat(result.status()).isEqualTo(ComposeRouterPlanResult.STATUS_ADDED);
        assertThat(result.routes().get(0).serviceName()).isEqualTo("frontend");
        assertThat(result.routes().get(0).root()).isTrue();
        assertThat(result.routerHostPort()).isEqualTo(18081);
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("자동 조정"));
    }

    @Test
    void keepsPreExistingConfigWithReservedName() {
        String compose = """
                services:
                  web:
                    ports: ["3000:3000"]
                  api:
                    ports: ["8080:8080"]
                configs:
                  gamjabox-caddyfile:
                    file: ./existing.conf
                """;

        ComposeRouterPlanResult result = planner.plan(compose, null, Map.of());

        Map<?, ?> root = new Yaml().load(result.enhancedComposeContent());
        Map<?, ?> configs = (Map<?, ?>) root.get("configs");
        assertThat(configs.containsKey("gamjabox-caddyfile")).isTrue();
        assertThat(configs.containsKey("gamjabox-caddyfile-2")).isTrue();
        assertThat(result.enhancedComposeContent()).contains("source: gamjabox-caddyfile-2");
    }

    @Test
    void createsUniquePathsWhenServiceSlugsWouldCollide() {
        String compose = """
                services:
                  web:
                    ports: ["3000:3000"]
                  foo_bar:
                    ports: ["8080:8080"]
                  foo-bar:
                    ports: ["8081:8081"]
                """;

        ComposeRouterPlanResult result = planner.plan(compose, null, Map.of());

        assertThat(result.routes()).extracting(ComposeRouterRoute::routePath)
                .containsExactly("/", "/foo-bar", "/foo-bar-2");
        assertThat(result.routerConfig())
                .contains("@route_foo_bar path /foo-bar /foo-bar/*")
                .contains("@route_foo_bar_2 path /foo-bar-2 /foo-bar-2/*");
    }

    @Test
    void keepsExistingReverseProxyUntouched() {
        String compose = """
                services:
                  frontend:
                    image: example/frontend
                    expose: [3000]
                  api:
                    image: example/api
                    expose: [8080]
                  edge:
                    image: nginx:alpine
                    ports: ["8088:80"]
                """;

        ComposeRouterPlanResult result = planner.plan(compose, null, Map.of());

        assertThat(result.status()).isEqualTo(ComposeRouterPlanResult.STATUS_ALREADY_CONFIGURED);
        assertThat(result.enhancedComposeContent()).isEqualTo(compose);
        assertThat(result.routerServiceName()).isEqualTo("edge");
    }

    @Test
    void doesNotMistakeStaticNginxFrontendForExistingRouter() {
        String compose = """
                services:
                  frontend:
                    image: nginx:alpine
                    ports: ["3000:80"]
                  api:
                    build: ./api
                    ports: ["8080:8080"]
                """;

        ComposeRouterPlanResult result = planner.plan(compose, null, Map.of());

        assertThat(result.status()).isEqualTo(ComposeRouterPlanResult.STATUS_ADDED);
        assertThat(result.routes()).extracting(ComposeRouterRoute::serviceName)
                .containsExactly("frontend", "api");
    }

    @Test
    void requestsOnlyUnknownServicePortsAndAcceptsOverrides() {
        String compose = """
                services:
                  web:
                    build: ./web
                  api:
                    build: ./api
                    expose: [8080]
                """;

        ComposeRouterPlanResult unresolved = planner.plan(compose, 19090, Map.of());
        ComposeRouterPlanResult resolved = planner.plan(compose, 19090, Map.of("web", 3000));

        assertThat(unresolved.status()).isEqualTo(ComposeRouterPlanResult.STATUS_NEEDS_INPUT);
        assertThat(unresolved.unresolvedServices())
                .extracting(ComposeRouterUnresolvedService::serviceName)
                .containsExactly("web");
        assertThat(resolved.status()).isEqualTo(ComposeRouterPlanResult.STATUS_ADDED);
        assertThat(resolved.routerHostPort()).isEqualTo(19090);
    }

    @Test
    void doesNotAddRouterForSingleApplicationPlusInfrastructure() {
        String compose = """
                services:
                  api:
                    ports: ["8080:8080"]
                  redis:
                    image: redis:7
                    expose: [6379]
                """;

        ComposeRouterPlanResult result = planner.plan(compose, null, Map.of());

        assertThat(result.status()).isEqualTo(ComposeRouterPlanResult.STATUS_NOT_REQUIRED);
    }
}
