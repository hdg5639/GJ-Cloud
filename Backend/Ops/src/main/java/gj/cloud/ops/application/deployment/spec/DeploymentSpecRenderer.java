package gj.cloud.ops.application.deployment.spec;

import gj.cloud.ops.application.deployment.dto.ComposeArtifact;
import gj.cloud.ops.application.deployment.dto.EnvironmentFile;
import gj.cloud.ops.application.deployment.dto.ExposedRoute;
import gj.cloud.ops.application.deployment.dto.HealthCheck;
import gj.cloud.ops.application.deployment.dto.UploadedFile;
import gj.cloud.ops.application.deployment.routing.ComposeRouterPlanResult;
import gj.cloud.ops.application.deployment.routing.ComposeRouterPlanner;
import gj.cloud.ops.application.deployment.routing.ComposeRouterRoute;
import gj.cloud.ops.domain.deployment.enums.SourceType;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// DeploymentSpec(JSON) → ComposeArtifact 렌더링. D-1(폼 입력)과 D-3(AI 생성)이 이 렌더러를 공유함 (F절 7·9단계).
// 렌더링 결과(composeContent)도 D-2와 동일하게 공통 Validator를 그대로 통과해야 함 — 이 클래스에서는 검증하지 않고
// DeploymentExecutor.enqueue()에 위임되는 기존 경로를 그대로 재사용함으로써 자동으로 보장됨.
@Component
@RequiredArgsConstructor
public class DeploymentSpecRenderer {

    private static final String DOCKERFILE_NAME = "Dockerfile.gamjabox";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final DockerfileGenerator dockerfileGenerator;
    private final ComposeRouterPlanner composeRouterPlanner;

    // DEP-002: 이전에는 모든 배포가 동일한 고정 비밀번호("gamjabox")를 사용했고, infra.expose.enabled=true를
    // 사용자가 직접 요청하면 그 고정 비밀번호 그대로 호스트에 노출되는 경로도 있었음 — expose 여부와 무관하게
    // 항상 배포별 랜덤 비밀번호를 생성해 두 문제를 함께 해소한다. 값은 새 채널을 만들지 않고 기존
    // composeContent(암호화 저장, 인증된 API로만 조회 가능 — SEC-010에서 확립된 경로)에 자연히 포함됨.
    private String generateRandomPassword() {
        byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public ComposeArtifact render(DeploymentSpec spec) {
        Map<String, Object> services = new LinkedHashMap<>();
        List<UploadedFile> uploadedFiles = new ArrayList<>();
        List<ExposedRoute> exposedRoutes = new ArrayList<>();
        List<HealthCheck> healthChecks = new ArrayList<>();

        for (ServiceSpec service : spec.services()) {
            boolean isStatic = service.artifact().type() == ArtifactType.STATIC_DIRECTORY;
            int effectivePort = isStatic ? 80 : (service.run().containerPort() != null ? service.run().containerPort() : 0);

            Map<String, Object> serviceBlock = new LinkedHashMap<>();
            Map<String, Object> build = new LinkedHashMap<>();
            build.put("context", service.context());

            boolean useExistingDockerfile = service.build().strategy() == BuildRunStrategy.DOCKERFILE;
            if (useExistingDockerfile) {
                // 저장소에 이미 있는 Dockerfile을 그대로 사용 — 우리가 생성하지 않음 (D-1/D-3과 달리 D-2 raw
                // compose 흐름과 동일하게 사용자가 제공한 빌드 정의가 그대로 authoritative함)
                build.put("dockerfile", "Dockerfile");
            } else {
                build.put("dockerfile", DOCKERFILE_NAME);
            }
            serviceBlock.put("build", build);
            serviceBlock.put("container_name", service.name());
            serviceBlock.put("restart", "unless-stopped");
            serviceBlock.put("networks", List.of(spec.network()));

            boolean exposed = service.expose() != null && service.expose().enabled();
            boolean httpExposed = exposed && "http".equalsIgnoreCase(service.expose().protocol());
            serviceBlock.put("labels", Map.of("gamjabox.router.enabled", httpExposed));
            if (exposed) {
                // Cloudflare Tunnel이 VM 호스트에서 붙으므로 외부 노출 대상은 호스트 포트 바인딩이 필요함
                serviceBlock.put("ports", List.of(effectivePort + ":" + effectivePort));
            }
            services.put(service.name(), serviceBlock);

            if (!useExistingDockerfile) {
                String dockerfileContent = dockerfileGenerator.generate(service);
                uploadedFiles.add(new UploadedFile(service.context() + "/" + DOCKERFILE_NAME,
                        dockerfileContent.getBytes(StandardCharsets.UTF_8)));
            }

            if (exposed) {
                String protocol = "http".equalsIgnoreCase(service.expose().protocol()) ? "HTTP" : "TCP";
                exposedRoutes.add(new ExposedRoute(service.name(), effectivePort, protocol, "PUBLIC", service.name(),
                        service.expose().customSubdomain()));

                String healthPath = service.expose().healthCheckPath();
                if (healthPath != null && !healthPath.isBlank()) {
                    healthChecks.add(new HealthCheck(service.name(), healthPath, effectivePort, null));
                }
            }
        }

        List<EnvironmentFile> environmentFiles = new ArrayList<>();
        Map<String, Object> volumeDefs = new LinkedHashMap<>();
        if (spec.infrastructure() != null) {
            // network 재사용(externalNetwork=true) 시 여러 배포가 같은 infra.type()을 쓸 수 있어, 볼륨명은
            // network가 아니라 배포 단위로 매번 새로 뽑는 접미사로 구분해 데이터 공유 사고를 막는다.
            String volumeSuffix = UUID.randomUUID().toString().substring(0, 8);
            for (InfrastructureSpec infra : spec.infrastructure()) {
                renderInfrastructure(infra, spec.network(), volumeSuffix, services, volumeDefs);
            }
        }

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("services", services);
        Map<String, Object> networkDef = new LinkedHashMap<>();
        // externalNetwork=true면 VM에 이미 존재하는 네트워크를 새로 만들지 않고 그대로 재사용
        networkDef.put(spec.network(), spec.externalNetwork() ? Map.of("external", true) : Map.of());
        root.put("networks", networkDef);
        if (!volumeDefs.isEmpty()) {
            root.put("volumes", volumeDefs);
        }

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        String composeContent = new Yaml(options).dump(root);

        long distinctCustomSubdomains = exposedRoutes.stream()
                .filter(route -> "HTTP".equalsIgnoreCase(route.protocol()))
                .map(ExposedRoute::customSubdomain)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .count();
        if (distinctCustomSubdomains <= 1) {
            ComposeRouterPlanResult routerPlan = composeRouterPlanner.plan(composeContent, null, Map.of());
            if (ComposeRouterPlanResult.STATUS_ADDED.equals(routerPlan.status())) {
                composeContent = routerPlan.enhancedComposeContent();
                Set<String> routedServices = routerPlan.routes().stream()
                        .map(ComposeRouterRoute::serviceName)
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
                String customSubdomain = exposedRoutes.stream()
                        .filter(route -> routedServices.contains(route.serviceName()))
                        .map(ExposedRoute::customSubdomain)
                        .filter(value -> value != null && !value.isBlank())
                        .findFirst()
                        .orElse(null);
                List<ExposedRoute> preservedRoutes = exposedRoutes.stream()
                        .filter(route -> !routedServices.contains(route.serviceName()))
                        .toList();
                String gatewayNickname = preservedRoutes.stream()
                        .anyMatch(route -> "gateway".equals(route.nickname()))
                        ? "http-gateway"
                        : "gateway";
                List<ExposedRoute> enhancedRoutes = new ArrayList<>();
                enhancedRoutes.add(new ExposedRoute(
                        ComposeRouterPlanner.ROUTER_SERVICE_NAME,
                        routerPlan.routerHostPort(),
                        "HTTP",
                        "PUBLIC",
                        gatewayNickname,
                        customSubdomain));
                enhancedRoutes.addAll(preservedRoutes);
                exposedRoutes = enhancedRoutes;
                Map<String, ComposeRouterRoute> routeByService = routerPlan.routes().stream()
                        .collect(java.util.stream.Collectors.toMap(ComposeRouterRoute::serviceName, route -> route));
                healthChecks = healthChecks.stream()
                        .map(check -> remapHealthCheck(check, routeByService, routerPlan.routerHostPort()))
                        .toList();
            }
        }

        return new ComposeArtifact(composeContent, environmentFiles, uploadedFiles, exposedRoutes, healthChecks, SourceType.TEMPLATE_SPEC);
    }

    private HealthCheck remapHealthCheck(
            HealthCheck check,
            Map<String, ComposeRouterRoute> routeByService,
            int routerHostPort
    ) {
        ComposeRouterRoute route = routeByService.get(check.serviceName());
        if (route == null) return check;
        String originalPath = check.path() == null || check.path().isBlank() ? "/" : check.path();
        String routedPath = route.root()
                ? originalPath
                : route.routePath() + (originalPath.startsWith("/") ? originalPath : "/" + originalPath);
        return new HealthCheck(
                ComposeRouterPlanner.ROUTER_SERVICE_NAME,
                routedPath,
                routerHostPort,
                null);
    }

    private void renderInfrastructure(InfrastructureSpec infra, String network, String volumeSuffix,
                                       Map<String, Object> services, Map<String, Object> volumeDefs) {
        Map<String, Object> block = new LinkedHashMap<>();
        Map<String, String> environment = new LinkedHashMap<>();
        String volumeName = infra.type() + "_data_" + volumeSuffix;

        switch (infra.type()) {
            case "postgresql" -> {
                block.put("image", "postgres:" + infra.version());
                environment.put("POSTGRES_PASSWORD", generateRandomPassword());
                environment.put("POSTGRES_USER", "gamjabox");
                environment.put("POSTGRES_DB", "gamjabox");
                block.put("volumes", List.of(volumeName + ":/var/lib/postgresql/data"));
                volumeDefs.put(volumeName, Map.of());
            }
            case "mysql" -> {
                block.put("image", "mysql:" + infra.version());
                environment.put("MYSQL_ROOT_PASSWORD", generateRandomPassword());
                environment.put("MYSQL_DATABASE", "gamjabox");
                block.put("volumes", List.of(volumeName + ":/var/lib/mysql"));
                volumeDefs.put(volumeName, Map.of());
            }
            case "redis" -> block.put("image", "redis:" + infra.version());
            case "mongodb" -> {
                block.put("image", "mongo:" + infra.version());
                block.put("volumes", List.of(volumeName + ":/data/db"));
                volumeDefs.put(volumeName, Map.of());
            }
            default -> throw new OpsException(OpsErrorCode.INVALID_COMPOSE);
        }

        if (!environment.isEmpty()) {
            block.put("environment", environment);
        }
        block.put("restart", "unless-stopped");
        block.put("networks", List.of(network));

        boolean exposed = infra.expose() != null && infra.expose().enabled();
        if (exposed) {
            // 인프라를 외부에 노출하는 건 이례적이지만 명시적으로 요청했다면 존중함 (D-3 expose.enabled 규약).
            // DEP-002: 비밀번호가 이제 배포별 랜덤값이라 expose=true여도 고정 자격증명 노출 문제는 없음.
            block.put("ports", List.of(defaultInfraPort(infra.type()) + ":" + defaultInfraPort(infra.type())));
        }

        services.put(infra.type(), block);
    }

    private int defaultInfraPort(String type) {
        return switch (type) {
            case "postgresql" -> 5432;
            case "mysql" -> 3306;
            case "redis" -> 6379;
            case "mongodb" -> 27017;
            default -> throw new OpsException(OpsErrorCode.INVALID_COMPOSE);
        };
    }
}
